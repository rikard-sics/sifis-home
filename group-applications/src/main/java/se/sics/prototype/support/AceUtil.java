/*******************************************************************************
 * Copyright (c) 2022, RISE AB
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions 
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, 
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, 
 *    this list of conditions and the following disclaimer in the documentation 
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS 
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT 
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR 
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT 
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT 
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY 
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT 
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE 
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *******************************************************************************/
package se.sics.prototype.support;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.HashSet;
import java.util.Set;

import com.upokecenter.cbor.CBORObject;

import se.sics.ace.AceException;
import se.sics.ace.Constants;

import org.eclipse.californium.cose.AlgorithmID;
import org.eclipse.californium.cose.CoseException;
import org.eclipse.californium.cose.KeyKeys;
import org.eclipse.californium.cose.OneKey;

/**
 * Convenient utility functions for ACE.
 * 
 */
public class AceUtil {

	/**
	 * Compute a digital signature
	 * 
	 * @param signKeyCurve Elliptic curve used to compute the signature
	 * @param privKey private key of the signer, used to compute the signature
	 * @param dataToSign content to sign
	 * @return The computed signature, or null in case of error
	 * 
	 */
	public static byte[] computeSignature(int signKeyCurve, PrivateKey privKey, byte[] dataToSign) {

		Signature signCtx = null;
		byte[] signature = null;

		try {
			if (signKeyCurve == KeyKeys.EC2_P256.AsInt32())
				signCtx = Signature.getInstance("SHA256withECDSA");
			else if (signKeyCurve == KeyKeys.OKP_Ed25519.AsInt32())
				signCtx = Signature.getInstance("NonewithEdDSA", "EdDSA");
			else {
				// At the moment, only ECDSA (EC2_P256) and EDDSA (Ed25519) are
				// supported
				System.err.println("Unsupported signature algorithm");
				return null;
			}

		} catch (NoSuchAlgorithmException e) {
			System.err.println("Unsupported signature algorithm: " + e.getMessage());
			return null;
		} catch (NoSuchProviderException e) {
			System.err.println("Unsopported security provider for signature computing: " + e.getMessage());
			return null;
		}

		try {
			if (signCtx != null)
				signCtx.initSign(privKey);
			else {
				System.err.println("Signature algorithm has not been initialized");
				return null;
			}
		} catch (InvalidKeyException e) {
			System.err.println("Invalid key excpetion - Invalid private key: " + e.getMessage());
			return null;
		}

		try {
			signCtx.update(dataToSign);
			signature = signCtx.sign();
		} catch (SignatureException e) {
			System.err.println("Failed signature computation: " + e.getMessage());
			return null;
		}

		return signature;

	}

	/**
	 * Verify the correctness of a digital signature
	 * 
	 * @param signKeyCurve Elliptic curve used to process the signature
	 * @param pubKey Public key of the signer, used to verify the signature
	 * @param signedData Data over which the signature has been computed
	 * @param expectedSignature Signature to verify
	 * @return True if the signature verifies correctly, false otherwise
	 */
	public static boolean verifySignature(int signKeyCurve, PublicKey pubKey, byte[] signedData,
			byte[] expectedSignature) {

		Signature signature = null;
		boolean success = false;

		try {
			if (signKeyCurve == KeyKeys.EC2_P256.AsInt32())
				signature = Signature.getInstance("SHA256withECDSA");
			else if (signKeyCurve == KeyKeys.OKP_Ed25519.AsInt32())
				signature = Signature.getInstance("NonewithEdDSA", "EdDSA");
			else {
				System.err.println("Unsupported signature algorithm");
				return false;
			}

		} catch (NoSuchAlgorithmException e) {
			System.err.println("Unsupported signature algorithm: " + e.getMessage());
			return false;
		} catch (NoSuchProviderException e) {
			System.err.println("Unsopported security provider for signature computing: " + e.getMessage());
			return false;
		}

		try {
			if (signature != null)
				signature.initVerify(pubKey);
			else {
				System.err.println("Signature algorithm has not been initialized");
				return false;
			}
		} catch (InvalidKeyException e) {
			System.err.println("Invalid key excpetion - Invalid public key: " + e.getMessage());
			return false;
		}

		try {
			signature.update(signedData);
			success = signature.verify(expectedSignature);
		} catch (SignatureException e) {
			System.err.println("Error during signature verification: " + e.getMessage());
			return false;
		}

		return success;

	}

	/**
	 * Add 'newRole' to the role set, encoded using the AIF-OSCORE-GROUPCOMM
	 * data model
	 * 
	 * @param currentRoleSet the current set of roles
	 * @param newRole the role to add to the current set
	 * 
	 * @return the updated role set
	 * @throws Exception on failure
	 */
	public static int addGroupOSCORERole(int currentRoleSet, short newRole) throws Exception {

		if (newRole < 1)
			throw new Exception("Invalid identifier of Group OSCORE role");

		int updatedRoleSet = 0;
		updatedRoleSet = currentRoleSet | (1 << newRole);

		return updatedRoleSet;

	}

	/**
	 * Build a CWT Claims Set (CCS) including a COSE Key within a "cnf" claim
	 * and an additional "sub" claim
	 * 
	 * @param identityKey The public key as a OneKey object
	 * @param subjectName The subject name associated to this key, it can be an
	 *            empty string
	 * @return The serialization of the CCS, or null in case of errors
	 */
	public static byte[] oneKeyToCCS(OneKey identityKey, String subjectName) {

		if (identityKey == null || subjectName == null)
			return null;

		CBORObject coseKeyMap = CBORObject.NewMap();
		coseKeyMap.Add(KeyKeys.KeyType.AsCBOR(), identityKey.get(KeyKeys.KeyType));
		if (identityKey.get(KeyKeys.KeyType) == KeyKeys.KeyType_OKP) {
			int curve = identityKey.get(KeyKeys.OKP_Curve).AsInt32();
			if (curve == KeyKeys.OKP_Ed25519.AsInt32() || curve == KeyKeys.OKP_Ed448.AsInt32()) {
				coseKeyMap.Add(KeyKeys.Algorithm.AsCBOR(), AlgorithmID.EDDSA.AsCBOR());
			}
			if (curve == KeyKeys.OKP_X25519.AsInt32() || curve == KeyKeys.OKP_X448.AsInt32()) {
				coseKeyMap.Add(KeyKeys.Algorithm.AsCBOR(), AlgorithmID.ECDH_ES_HKDF_256.AsCBOR());
			}
			coseKeyMap.Add(KeyKeys.OKP_Curve.AsCBOR(), identityKey.get(KeyKeys.OKP_Curve));
			coseKeyMap.Add(KeyKeys.OKP_X.AsCBOR(), identityKey.get(KeyKeys.OKP_X));
		} else if (identityKey.get(KeyKeys.KeyType) == KeyKeys.KeyType_EC2) {
			int curve = identityKey.get(KeyKeys.EC2_Curve).AsInt32();
			if (curve == KeyKeys.EC2_P256.AsInt32()) {
				coseKeyMap.Add(KeyKeys.Algorithm.AsCBOR(), AlgorithmID.ECDSA_256.AsCBOR());
			}
			if (curve == KeyKeys.EC2_P384.AsInt32()) {
				coseKeyMap.Add(KeyKeys.Algorithm.AsCBOR(), AlgorithmID.ECDSA_384.AsCBOR());
			}
			if (curve == KeyKeys.EC2_P521.AsInt32()) {
				coseKeyMap.Add(KeyKeys.Algorithm.AsCBOR(), AlgorithmID.ECDSA_512.AsCBOR());
			}
			coseKeyMap.Add(KeyKeys.EC2_Curve.AsCBOR(), identityKey.get(KeyKeys.EC2_Curve));
			coseKeyMap.Add(KeyKeys.EC2_X.AsCBOR(), identityKey.get(KeyKeys.EC2_X));
			coseKeyMap.Add(KeyKeys.EC2_Y.AsCBOR(), identityKey.get(KeyKeys.EC2_Y));
		} else {
			return null;
		}

		CBORObject cnfMap = CBORObject.NewMap();
		cnfMap.Add(Constants.COSE_KEY, coseKeyMap);

		CBORObject claimSetMap = CBORObject.NewMap();
		claimSetMap.Add(Constants.SUB, subjectName);
		claimSetMap.Add(Constants.CNF, cnfMap);

		// Debug print
		System.out.println(claimSetMap);

		return claimSetMap.EncodeToBytes();

	}

	/**
	 * Extract a public key from a CWT Claims Set (CCS) and return it as a
	 * OneKey object
	 * 
	 * @param ccs input CCS to extract key from
	 * 
	 * @return The CCS as a CBOR map, or null in case of errors
	 */
	public static OneKey ccsToOneKey(CBORObject ccs) {

		if (ccs == null)
			return null;

		if (!ccs.ContainsKey(Constants.CNF) || !ccs.get(Constants.CNF).ContainsKey(Constants.COSE_KEY))
			return null;

		CBORObject pubKeyCBOR = ccs.get(Constants.CNF).get(Constants.COSE_KEY);

		OneKey pubKey = null;
		try {
			pubKey = new OneKey(pubKeyCBOR);
		} catch (CoseException e) {
			System.err.println("Error when building a OneKey from a CCS: " + e.getMessage());
			return null;
		}

		return pubKey;

	}

	/**
	 * Convert a byte array into an equivalent unsigned integer. The input byte
	 * array can be up to 4 bytes in size.
	 *
	 * N.B. If the input array is 4 bytes in size, the returned integer may be
	 * negative! The calling method has to check, if relevant!
	 * 
	 * @param bytes
	 * @return the converted integer
	 */
	public static int bytesToInt(final byte[] bytes) {

		if (bytes.length > 4)
			return -1;

		int ret = 0;

		// Big-endian
		for (int i = 0; i < bytes.length; i++)
			ret = ret + (bytes[bytes.length - 1 - i] & 0xFF) * (int) (Math.pow(256, i));

		/*
		 * // Little-endian for (int i = 0; i < bytes.length; i++) ret = ret +
		 * (bytes[i] & 0xFF) * (int) (Math.pow(256, i));
		 */

		return ret;

	}

	/**
	 * Return the array of roles included in the specified role set, encoded
	 * using the AIF-OSCORE-GROUPCOMM data model
	 * 
	 * @param roleSet the set of roles, encoded using the AIF-OSCORE-GROUPCOMM
	 *            data model
	 * 
	 * @return The set of role identifiers specified in the role set
	 * @throws AceException if the reserved role is requested (identifier 1,
	 *             hence 'roleSet' has an odd value)
	 */

	public static Set<Integer> getGroupOSCORERoles(int roleSet) throws AceException {

		if ((roleSet % 2) == 1)
			throw new AceException("Invalid identifier of Group OSCORE role");

		Set<Integer> mySet = new HashSet<Integer>();
		int roleIdentifier = 0;

		while (roleSet != 0) {
			roleSet = roleSet >>> 1;
			roleIdentifier++;
			if ((roleSet & 1) != 0) {
				mySet.add(Integer.valueOf(roleIdentifier));
			}
		}

		return mySet;

	}

}
