/*******************************************************************************
 * Copyright (c) 2018 RISE SICS and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *    Joakim Brorsson
 *    Ludwig Seitz (RISE SICS)
 *    Tobias Andersson (RISE SICS)
 *    Rikard Höglund (RISE SICS)
 *    
 ******************************************************************************/
package org.eclipse.californium.oscore;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.californium.core.Utils;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.Message;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.cose.Encrypt0Message;

import com.upokecenter.cbor.CBORObject;

import org.eclipse.californium.cose.AlgorithmID;
import org.eclipse.californium.cose.Attribute;
import org.eclipse.californium.cose.CoseException;
import org.eclipse.californium.cose.CounterSign1;
import org.eclipse.californium.cose.HeaderKeys;
import org.eclipse.californium.cose.OneKey;
import org.eclipse.californium.elements.util.Bytes;
import org.eclipse.californium.oscore.group.GroupRecipientCtx;

/**
 * 
 * Gathers generalized methods for decryption and decompression of OSCORE
 * protected messages. Also provides decoding of the encoded OSCORE option
 *
 */
public abstract class Decryptor {
	/**
	 * Java 1.6 compatibility.
	 */
	public static final int INTEGER_BYTES = Integer.SIZE / Byte.SIZE;

	/**
	 * The logger
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(Decryptor.class);

	/**
	 * Empty option set
	 */
	protected static final OptionSet EMPTY = new OptionSet();

	/**
	 * Decrypts and decodes the message.
	 * 
	 * @param enc the COSE structure
	 * @param message the message
	 * @param ctx the OSCore context
	 * @param seqByToken the sequence number
	 * 
	 * @return the decrypted plaintext
	 *
	 * @throws OSException if decryption or decoding fails
	 */
	protected static byte[] decryptAndDecode(Encrypt0Message enc, Message message, OSCoreCtx ctx, Integer seqByToken)
			throws OSException {
		int seq = -2;
		boolean isRequest = message instanceof Request;
		byte[] nonce = null;
		byte[] partialIV = null;
		byte[] aad = null;

		AlgorithmID decryptionAlg = ctx.getAlg();
		CBORObject piv = enc.findAttribute(HeaderKeys.PARTIAL_IV);

		if (isRequest) {

			if (piv == null) {
				LOGGER.error("Decryption failed: no partialIV in request");
				throw new OSException(ErrorDescriptions.DECRYPTION_FAILED);
			} else {

				partialIV = piv.GetByteString();
				partialIV = expandToIntSize(partialIV);
				seq = ByteBuffer.wrap(partialIV).getInt();
				
				//Note that the code below can throw an OSException when replays are detected
				ctx.checkIncomingSeq(seq);
				if (ctx.isGroupContext()) {
					assert ctx instanceof GroupRecipientCtx;
				}

				nonce = OSSerializer.nonceGeneration(partialIV, ctx.getRecipientId(), ctx.getCommonIV(),
						ctx.getIVLength());
				aad = OSSerializer.serializeAAD(CoAP.VERSION, ctx.getAlg(), seq, ctx.getRecipientId(), message.getOptions());
			}
		} else {
			if (seqByToken == null) {
				LOGGER.error("Decryption failed: the arrived response is not connected to a request we sent");
				throw new OSException(ErrorDescriptions.DECRYPTION_FAILED);
			}
		
			//Sequence number taken from original request
			seq = seqByToken;

			if (piv == null) {
				//Use the partialIV that arrived in the original request (response has no partial IV)

				partialIV = ByteBuffer.allocate(INTEGER_BYTES).putInt(seq).array();
				nonce = OSSerializer.nonceGeneration(partialIV,	ctx.getSenderId(), ctx.getCommonIV(), 
						ctx.getIVLength());
			} else {
				//Since the response contains a partial IV use it for nonce calculation

				partialIV = piv.GetByteString();
				partialIV = expandToIntSize(partialIV);
				nonce = OSSerializer.nonceGeneration(partialIV, ctx.getRecipientId(), ctx.getCommonIV(),
						ctx.getIVLength());
			}

			//Nonce calculation uses partial IV in response (if present).
			//AAD calculation always uses partial IV (seq. nr.) of original request.  
			aad = OSSerializer.serializeAAD(CoAP.VERSION, ctx.getAlg(), seq, ctx.getSenderId(), message.getOptions());
		}

		System.out.println("Decrypting incoming " + message.getClass().getSimpleName());
		// System.out.println("Key " + Utils.toHexString(ctx.getRecipientKey()));
		// System.out.println("PartialIV " + Utils.toHexString(partialIV));
		// System.out.println("Nonce " + Utils.toHexString(nonce));
		// System.out.println("AAD " + Utils.toHexString(aad));

		byte[] plaintext = null;
		byte[] key = ctx.getRecipientKey();

		// Handle Group OSCORE messages
		CounterSign1 sign = null;
		boolean groupModeMessage = OptionJuggle.getGroupModeBit(message.getOptions().getOscore());
		if (ctx.isGroupContext()) {
			LOGGER.debug("Decrypting incoming " + message.getClass().getSimpleName()
					+ " using Group OSCORE. Pairwise mode: " + !groupModeMessage);

			// Update external AAD value for Group OSCORE
			aad = OSSerializer.updateAADForGroup(ctx, aad, message);

			System.out.println("Decrypting incoming " + message.getClass().getSimpleName() + ", using pairwise mode: "
					+ !groupModeMessage);
			// System.out.println("Decrypting incoming " + message.getClass().getSimpleName() + " with AAD "
			// 		+ Utils.toHexString(aad));

			// System.out.println("Decrypting incoming " + message.getClass().getSimpleName() + " with nonce "
			// 		+ Utils.toHexString(nonce));

			// If group mode is used prepare the signature checking
			if (groupModeMessage) {
				decryptionAlg = ((GroupRecipientCtx) ctx).getAlgSignEnc();
				// Decrypt the signature.
				if (isRequest || piv != null) {
					byte[] pivFromMessage = enc.findAttribute(HeaderKeys.PARTIAL_IV).GetByteString();
					decryptSignature(enc, sign, (GroupRecipientCtx) ctx, pivFromMessage, ctx.getRecipientId(),
							isRequest);
				} else {
					byte[] pivFromOther = OSSerializer.stripZeroes(ByteBuffer.allocate(5).putInt(seq).array());
					decryptSignature(enc, sign, (GroupRecipientCtx) ctx, pivFromOther, ctx.getSenderId(), isRequest);
				}

				sign = prepareCheckSignature(enc, ctx, aad, message);
			} else {
				// If this is a pairwise response use the pairwise key
				key = ((GroupRecipientCtx) ctx).getPairwiseRecipientKey();
			}
		}

		enc.setExternal(aad);

		// Check signature before decrypting
		if (groupModeMessage) {
			// Verify the signature
			boolean signatureCorrect = checkSignature(enc, sign);
			LOGGER.debug("Signature verification succeeded: " + signatureCorrect);
		}

		try {
			// TODO: Get and set Recipient ID (KID) here too?
			enc.addAttribute(HeaderKeys.Algorithm, decryptionAlg.AsCBOR(), Attribute.DO_NOT_SEND);
			enc.addAttribute(HeaderKeys.IV, CBORObject.FromObject(nonce), Attribute.DO_NOT_SEND);
			plaintext = enc.decrypt(key);

		} catch (CoseException e) {
			LOGGER.error(ErrorDescriptions.DECRYPTION_FAILED + " " + e.getMessage());
			throw new OSException(ErrorDescriptions.DECRYPTION_FAILED + " " + e.getMessage());
		}

		return plaintext;
	}

	/**
	 * @param partialIV partial IV to expand
	 * @return partial IV as byte array length of int
	 * 
	 * @throws OSException if the partial IV is longer than length of int
	 */
	private static byte[] expandToIntSize(byte[] partialIV) throws OSException {
		if (partialIV.length > INTEGER_BYTES) {
			LOGGER.error("The partial IV is: " + partialIV.length + " long, " + INTEGER_BYTES + " was expected");
			throw new OSException("Partial IV too long");
		} else if (partialIV.length == INTEGER_BYTES) {
			return partialIV;
		}
		byte[] ret = new byte[INTEGER_BYTES];
		for (int i = 0; i < partialIV.length; i++) {
			ret[INTEGER_BYTES - partialIV.length + i] = partialIV[i];
		}
		return ret;

	}

	/**
	 * @param protectedData the protected data to decrypt
	 * @return the COSE structure
	 */
	protected static Encrypt0Message prepareCOSEStructure(byte[] protectedData) {
		Encrypt0Message enc = new Encrypt0Message(false, true);
		try {
			enc.DecodeFromCBORObject(CBORObject.DecodeFromBytes(protectedData));
		} catch (CoseException e) {
			e.printStackTrace();
		}
		return enc;
	}

	/**
	 * Decompress the message.
	 * 
	 * @param cipherText the encrypted data
	 * @param message the received message
	 * @return the Encrypt0Message
	 * @throws OSException if OSCORE option fails to decode
	 */
	protected static Encrypt0Message decompression(byte[] cipherText, Message message) throws OSException {
		Encrypt0Message enc = new Encrypt0Message(false, true);

		//Added try-catch for general Exception. The array manipulation can cause exceptions.
		try {
			decodeObjectSecurity(message, enc);
		} catch (OSException e) {
			LOGGER.error(e.getMessage());
			throw e;
		} catch (Exception e) {
			LOGGER.error("Failed to decode object security option.");
			throw new OSException("Failed to decode object security option.");
		}

		if (cipherText != null)
			enc.setEncryptedContent(cipherText);
		return enc;
	}

	/**
	 * Decodes the Object-Security value.
	 * 
	 * @param message the received message
	 * @param enc the Encrypt0Message object
	 * @throws OSException if OSCORE option fails to decode
	 */
	private static void decodeObjectSecurity(Message message, Encrypt0Message enc) throws OSException {
		byte[] total = message.getOptions().getOscore();

		/**
		 * If the OSCORE option value is a zero length byte array
		 * it represents a byte array of length 1 with a byte 0x00
		 * See https://tools.ietf.org/html/draft-ietf-core-object-security-16#section-2  
		 */
		if (total.length == 0) {
			total = new byte[] { 0x00 };
		}
		
		byte flagByte = total[0];

		int n = flagByte & 0x07;
		int k = flagByte & 0x08;
		int h = flagByte & 0x10;

		byte[] partialIV = null;
		byte[] kid = null;
		byte[] kidContext = null;
		int index = 1;

		//Parsing Partial IV
		if (n > 0) {
			try {
				partialIV = Arrays.copyOfRange(total, index, index + n);
				index += n;
			} catch (Exception e) {
				LOGGER.error("Partial_IV is missing from message when it is expected.");
				throw new OSException(ErrorDescriptions.FAILED_TO_DECODE_COSE);
			}
		}

		//Parsing KID Context
		if (h != 0) {
			int s = total[index];

			kidContext = Arrays.copyOfRange(total, index + 1, index + 1 + s);

			index += s + 1;

			if (s > 0) {
				LOGGER.info("Received KID Context: " + Utils.toHexString(kidContext));
			} else {
				LOGGER.error("Kid context is missing from message when it is expected.");
				throw new OSException(ErrorDescriptions.FAILED_TO_DECODE_COSE);
			}
		}

		//Parsing KID
		if (k != 0) {
			kid = Arrays.copyOfRange(total, index, total.length);
		} else {
			if (message instanceof Request) {
				LOGGER.error("Kid is missing from message when it is expected.");
				throw new OSException(ErrorDescriptions.FAILED_TO_DECODE_COSE);
			}
		}

		//Adding parsed data to Encrypt0Message object
		try {
			if (partialIV != null) {
				enc.addAttribute(HeaderKeys.PARTIAL_IV, CBORObject.FromObject(partialIV), Attribute.UNPROTECTED);
			}
			if (kid != null) {
				enc.addAttribute(HeaderKeys.KID, CBORObject.FromObject(kid), Attribute.UNPROTECTED);
			}
			//COSE Header parameter for KID Context defined with label 10
			//https://www.iana.org/assignments/cose/cose.xhtml
			if (kidContext != null) {
				enc.addAttribute(CBORObject.FromObject(10), CBORObject.FromObject(kidContext), Attribute.UNPROTECTED);
			}
		} catch (CoseException e) {
			LOGGER.error("COSE processing of message failed.");
			e.printStackTrace();
		}
	}

	/**
	 * Replaces the message's options with a new OptionSet which doesn't contain
	 * any of the non-special E options as outer options
	 * 
	 * @param message the received message
	 */
	protected static void discardEOptions(Message message) {
		OptionSet newOptions = OptionJuggle.discardEOptions(message.getOptions());
		message.setOptions(newOptions);
	}

	// TODO: Remove unneeded lines
	private static boolean checkSignature(Encrypt0Message enc, CounterSign1 sign) throws OSException {

		boolean countersignatureValid = false;

		try {
			countersignatureValid = enc.validate(sign);
		} catch (CoseException e) {
			LOGGER.error("Countersignature checking procedure failed.");
			e.printStackTrace();
		}

		if (countersignatureValid == false) {
			LOGGER.error(ErrorDescriptions.COUNTERSIGNATURE_CHECK_FAILED);
			throw new OSException(ErrorDescriptions.COUNTERSIGNATURE_CHECK_FAILED);
		}

		System.out.println("Countersignature verified as valid");

		return countersignatureValid;
	}

	// TODO: Remove unneeded lines
	private static CounterSign1 prepareCheckSignature(Encrypt0Message enc, OSCoreCtx ctx, byte[] aad, Message message) {

		CounterSign1 sign = null;
		GroupRecipientCtx recipientCtx = (GroupRecipientCtx) ctx;

		// First remove the countersignature from the payload
		byte[] full_payload = null;
		try {
			full_payload = enc.getEncryptedContent();

			// Set new truncated ciphertext
			int countersignatureLength = recipientCtx.getCountersignatureLen();
			byte[] countersignatureBytes = Arrays.copyOfRange(full_payload,
					full_payload.length - countersignatureLength, full_payload.length);
			byte[] ciphertext = Arrays.copyOfRange(full_payload, 0, full_payload.length - countersignatureLength);
			enc.setEncryptedContent(ciphertext);

			// Now actually prepare to check the countersignature
			OneKey recipientPublicKey = recipientCtx.getPublicKey();
			// countersignatureBytes[3] = (byte) 0xff; // Corrupt
			// countersignature
			sign = new CounterSign1(countersignatureBytes);
			sign.setKey(recipientPublicKey);

			CBORObject signAlg = recipientCtx.getAlgSign().AsCBOR();
			sign.addAttribute(HeaderKeys.Algorithm, signAlg, Attribute.DO_NOT_SEND);
			byte[] signAad = aad;

			sign.setExternal(signAad);

			System.out.println("Checking signature for incoming " + message.getClass().getSimpleName()
					+ " with sign AAD " + Utils.toHexString(signAad));
		} catch (Exception e) {
			LOGGER.error("Countersignature verification procedure failed.");
			e.printStackTrace();
		}

		return sign;
	}

	private static void decryptSignature(Encrypt0Message enc, CounterSign1 sign, GroupRecipientCtx ctx,
			byte[] partialIV,
			byte[] kid, boolean isRequest) {

		// Derive the keystream
		String digest = "";
		if (ctx.getKdf().toString().contains("SHA_256")) {
			digest = "SHA256";
		} else if (ctx.getKdf().toString().contains("SHA_512")) {
			digest = "SHA512";
		}

		CBORObject info = CBORObject.NewArray();
		int keyLength = ctx.getCommonCtx().getCountersignatureLen();

		info = CBORObject.NewArray();
		info.Add(kid);
		info.Add(ctx.getIdContext());
		info.Add(isRequest);
		info.Add(keyLength);

		byte[] groupEncryptionKey = ctx.getCommonCtx().getGroupEncryptionKey();
		byte[] keystream = null;
		try {
			keystream = OSCoreCtx.deriveKey(groupEncryptionKey, partialIV, keyLength, digest, info.EncodeToBytes());

		} catch (CoseException e) {
			System.err.println(e.getMessage());
		}

		// System.out.println("===");
		// System.out.println("D Signature keystream: " + Utils.toHexString(keystream));
		// System.out.println("D groupEncryptionKey: " + Utils.toHexString(groupEncryptionKey));
		// System.out.println("D partialIV: " + Utils.toHexString(partialIV));
		// System.out.println("D kid: " + Utils.toHexString(kid));
		// System.out.println("D IdContext: " + Utils.toHexString(ctx.getIdContext()));
		// System.out.println("D isRequest: " + isRequest);
		// System.out.println("===");

		// Now actually decrypt the signature
		byte[] full_payload = null;
		try {
			full_payload = enc.getEncryptedContent();
		} catch (CoseException e) {
			LOGGER.error("Countersignature verification procedure failed.");
			e.printStackTrace();
		}
		byte[] countersignBytes = Arrays.copyOfRange(full_payload, full_payload.length - keyLength,
				full_payload.length);
		byte[] ciphertext = Arrays.copyOfRange(full_payload, 0, full_payload.length - keyLength);

		byte[] decryptedCountersign = new byte[keystream.length];
		for (int i = 0; i < keystream.length; i++) {
			decryptedCountersign[i] = (byte) (countersignBytes[i] ^ keystream[i]);
		}

		// Replace the signature in the Encrypt0 object
		enc.setEncryptedContent(Bytes.concatenate(ciphertext, decryptedCountersign));
	}
}
