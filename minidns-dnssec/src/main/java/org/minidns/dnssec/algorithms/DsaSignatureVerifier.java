/*
 * Copyright 2015-2018 the original author or authors
 *
 * This software is licensed under the Apache License, Version 2.0,
 * the GNU Lesser General Public License version 2 or later ("LGPL")
 * and the WTFPL.
 * You may choose either license to govern your use of this software only
 * upon the condition that you accept all of the terms of either
 * the Apache License 2.0, the LGPL 2.1+ or the WTFPL.
 */
package org.minidns.dnssec.algorithms;

import org.minidns.dnssec.DnssecValidationFailedException.DnssecInvalidKeySpecException;
import org.minidns.dnssec.DnssecValidationFailedException.DataMalformedException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.InvalidKeySpecException;

class DsaSignatureVerifier extends JavaSecSignatureVerifier {
    private static final int LENGTH = 20;

    public DsaSignatureVerifier(String algorithm) throws NoSuchAlgorithmException {
        super("DSA", algorithm);
    }

    @Override
    protected byte[] getSignature(byte[] rrsigData) throws DataMalformedException {
        DataInput dis = new DataInputStream(new ByteArrayInputStream(rrsigData));
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);

        try {
        // Convert RFC 2536 to ASN.1
        @SuppressWarnings("unused")
        byte t = dis.readByte();

        byte[] r = new byte[LENGTH];
        dis.readFully(r);
        int rlen = (r[0] < 0) ? LENGTH + 1 : LENGTH;

        byte[] s = new byte[LENGTH];
        dis.readFully(s);
        int slen = (s[0] < 0) ? LENGTH + 1 : LENGTH;

        dos.writeByte(0x30);
        dos.writeByte(rlen + slen + 4);

        dos.writeByte(0x2);
        dos.writeByte(rlen);
        if (rlen > LENGTH)
            dos.writeByte(0);
        dos.write(r);

        dos.writeByte(0x2);
        dos.writeByte(slen);
        if (slen > LENGTH)
            dos.writeByte(0);
        dos.write(s);
        } catch (IOException e) {
            throw new DataMalformedException(e, rrsigData);
        }

        return bos.toByteArray();
    }

    @Override
    protected PublicKey getPublicKey(byte[] key) throws DataMalformedException, DnssecInvalidKeySpecException {
        DataInput dis = new DataInputStream(new ByteArrayInputStream(key));
        BigInteger subPrime, prime, base, pubKey;

        try {
            int t = dis.readUnsignedByte();

            byte[] subPrimeBytes = new byte[LENGTH];
            dis.readFully(subPrimeBytes);
            subPrime = new BigInteger(1, subPrimeBytes);

            byte[] primeBytes = new byte[64 + t * 8];
            dis.readFully(primeBytes);
            prime = new BigInteger(1, primeBytes);

            byte[] baseBytes = new byte[64 + t * 8];
            dis.readFully(baseBytes);
            base = new BigInteger(1, baseBytes);

            byte[] pubKeyBytes = new byte[64 + t * 8];
            dis.readFully(pubKeyBytes);
            pubKey = new BigInteger(1, pubKeyBytes);
        } catch (IOException e) {
            throw new DataMalformedException(e, key);
        }

        try {
            return getKeyFactory().generatePublic(new DSAPublicKeySpec(pubKey, prime, subPrime, base));
        } catch (InvalidKeySpecException e) {
            throw new DnssecInvalidKeySpecException(e);
        }
    }
}
