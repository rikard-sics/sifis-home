/*******************************************************************************

 * Original from https://github.com/cose-wg/COSE-JAVA Commit 1a20373
 *
 * Copyright (c) 2016, Jim Schaad
 * Copyright (c) 2018, Ludwig Seitz, RISE SICS
 * Copyright (c) 2018, Rikard HÃ¶glund, RISE SICS
 * All rights reserved.

 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:

 * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.

 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.

 * Neither the name of COSE-JAVA nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
 * THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * Contributors:
 *    Achim Kraus (Bosch Software Innovations GmbH) - update to cbor 4.0.0
 *                                                    align with cose 1.0
 *                                                    commit 629912b94ea80c4c6
 ******************************************************************************/
package org.eclipse.californium.cose;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.californium.elements.util.StandardCharsets;

/**
 * The Message class provides a common class that all of the COSE message classes
 * inherit from.  It provides the function used for decoding all of the known
 * messages.
 * 
 * @author jimsch
 */
public abstract class Message extends Attribute {
    /**
     * Is the tag identifying the message emitted?
     */
    protected boolean emitTag = true;
    
    /**
     * Is the content emitted as part of the message?
     */
    protected boolean emitContent = true;
    
    /**
     * What message tag identifies this message?
    */
    protected MessageTag messageTag = MessageTag.Unknown;
    
    /**
     * What is the plain text content of the message.
     */
    protected byte[] rgbContent = null;
  
    /**
     * Decode a COSE message object.  This function assumes that the message
     * has a leading CBOR tag to identify the message type.  If this is not
     * true then use {#link DecodeFromBytes(byte[], MessageTag)}.
     * 
     * @param rgbData byte stream to be decoded
     * @return the decoded message object
     * @throws CoseException on a decode failure
     */
    public static Message DecodeFromBytes(byte[] rgbData) throws CoseException {
        return DecodeFromBytes(rgbData, MessageTag.Unknown);
    }
    
    /**
     * Decode a COSE message object. Use a value of {@code MessageTag.Unknown}
     * to decode a generic structure with tagging.  Use a specific value if
     * the tagging is absent or if a known structure is passed in.
     * 
     * @param rgbData byte stream to be decoded
     * @param defaultTag assumed message type to be decoded
     * @return the decoded message object
     * @throws CoseException on a decode failure.
     */
    public static Message DecodeFromBytes(byte[] rgbData, MessageTag defaultTag) throws CoseException {
        CBORObject messageObject = CBORObject.DecodeFromBytes(rgbData);
        
        if (messageObject.getType() != CBORType.Array)  throw new CoseException("Message is not a COSE security Message");
        
        if (messageObject.isTagged()) {
            if (messageObject.getTagCount() != 1) throw new CoseException("Malformed message - too many tags");
            
            if (defaultTag == MessageTag.Unknown) {
                defaultTag = MessageTag.FromInt(messageObject.getMostInnerTag().ToInt32Unchecked());
            }
            else if (defaultTag != MessageTag.FromInt(messageObject.getMostInnerTag().ToInt32Unchecked())) {
                throw new CoseException("Passed in tag does not match actual tag");
            }
        }
        
        Message msg;
        
        switch (defaultTag) {
            case Unknown: // Unknown
                throw new CoseException("Message was not tagged and no default tagging option given");
		
            case Encrypt:
			msg = new EncryptMessage();
			break;

		case Encrypt0:
			msg = new Encrypt0Message();
			break;

            case MAC: 
			msg = new MACMessage();
			break;

		case MAC0:
			msg = new MAC0Message();
			break;

            case Sign1:
			msg = new Sign1Message();
			break;

            case Sign:
			msg = new SignMessage();
			break;
                
            default:
                throw new CoseException("Message is not recognized as a COSE security Object");
        }
    
        msg.DecodeFromCBORObject(messageObject);
        return msg;
        
    }

    /**
     * Encode the message to a byte array.  This function will force cryptographic operations to be executed as needed.
     * 
     * @return byte encoded object 
     * @throws CoseException Internal COSE Exception
     */
    public byte[] EncodeToBytes() throws CoseException {
        return EncodeToCBORObject().EncodeToBytes();
    }

    /**
     * Given a CBOR tree, parse the message.  This is an abstract function that is implemented for each different supported COSE message. 
     * 
     * @param messageObject CBORObject to be converted to a message.
     * @throws CoseException Internal COSE Exception
     */
    
    protected abstract void DecodeFromCBORObject(CBORObject messageObject) throws CoseException;
    
    /**
     * Encode the COSE message object to a CBORObject tree.  This function call will force cryptographic operations to be executed as needed.
     * This is an internal function, as such it does not add the tag on the front and is implemented on a per message object.
     * 
     * @return CBORObject representing the message.
     * @throws CoseException Internal COSE Exception
     */
    protected abstract CBORObject EncodeCBORObject() throws CoseException;
    
    /**
     * Encode the COSE message object to a CBORObject tree.  This function call will force cryptographic operations to be executed as needed.
     * 
     * @return CBORObject representing the message.
     * @throws CoseException Internal COSE Exception
     */
    public CBORObject EncodeToCBORObject() throws CoseException {
        CBORObject obj;
        
        obj = EncodeCBORObject();
        
        if (emitTag) {
            obj = CBORObject.FromObjectAndTag(obj, messageTag.value);
        }
        
        return obj;
    }

    /**
     * Return the content bytes of the message
     * 
     * @return bytes of the content
     */
    public byte[] GetContent() {
        return rgbContent;
    }
    
    /**
     * Does the message current have content?
     * 
     * @return true if it has content 
     */
    public boolean HasContent() {
        return rgbContent != null;
    }
    
    /**
     * Set the content bytes of the message.  If the message was transmitted with 
     * detached content, this must be called before doing cryptographic processing on the message.
     * 
     * @param rgbData bytes to set as the content
     */
    public void SetContent(byte[] rgbData) {
        rgbContent = rgbData;
    }
    
    /**
     * Set the content bytes as a text string.  The string will be encoded using UTF8 into a byte string.
     * 
     * @param strData string to set as the content
     */
    public void SetContent(String strData) {
        rgbContent = strData.getBytes(StandardCharsets.UTF_8);
    }

	List<CounterSign> counterSignList = new ArrayList<CounterSign>();
	CounterSign1 counterSign1;

	public void addCountersignature(CounterSign countersignature) {
		counterSignList.add(countersignature);
	}

	public List<CounterSign> getCountersignerList() {
		return counterSignList;
	}

	public CounterSign1 getCountersign1() {
		return counterSign1;
	}

	public void setCountersign1(CounterSign1 value) {
		counterSign1 = value;
	}

	protected void ProcessCounterSignatures() throws CoseException {
		if (!counterSignList.isEmpty()) {
			if (counterSignList.size() == 1) {
				counterSignList.get(0).sign(rgbProtected, rgbContent);
				addAttribute(HeaderKeys.CounterSignature, counterSignList.get(0).EncodeToCBORObject(),
						Attribute.UNPROTECTED);
			} else {
				CBORObject list = CBORObject.NewArray();
				for (CounterSign sig : counterSignList) {
					sig.sign(rgbProtected, rgbContent);
					list.Add(sig.EncodeToCBORObject());
				}
				addAttribute(HeaderKeys.CounterSignature, list, Attribute.UNPROTECTED);
			}
		}

		if (counterSign1 != null) {
			counterSign1.sign(rgbProtected, rgbContent);
			addAttribute(HeaderKeys.CounterSignature0, counterSign1.EncodeToCBORObject(), Attribute.UNPROTECTED);
		}
	}

	public boolean validate(CounterSign1 countersignature) throws CoseException {
		return countersignature.validate(rgbProtected, rgbContent);
	}

	public boolean validate(CounterSign countersignature) throws CoseException {
		return countersignature.validate(rgbProtected, rgbContent);
	}
}
