package org.hurricane.driver;

import java.util.ArrayList;
import java.io.IOException;
import java.math.BigInteger;
import org.hurricane.driver.StreamInterface;
import org.hurricane.driver.datatypes.AtomCacheRef;
import org.hurricane.driver.datatypes.Atom;
import org.hurricane.driver.datatypes.Reference;
import org.hurricane.driver.datatypes.Port;
import org.hurricane.driver.datatypes.Pid;
import org.hurricane.driver.datatypes.Tuple;
import org.hurricane.driver.datatypes.Nil;
import org.hurricane.driver.datatypes.Binary;
import org.hurricane.driver.datatypes.NewFunction;
import org.hurricane.driver.datatypes.ErlFunction;
import org.hurricane.driver.datatypes.Export;
import org.hurricane.driver.datatypes.NewReference;
import org.hurricane.driver.datatypes.BitBinary;

public class Decoder {
    public static Long unpackNumber(byte[] bytes) {
        Long value = 0L;
        Integer shift = 0;
        for (Integer i = bytes.length - 1; i >= 0; i--) {
            value |= (bytes[i] & 0xff) << (shift * 8);
            shift++;
        }
        return value;
    }

    public static AtomCacheRef decodeAtomCacheRef(StreamInterface stream) throws IOException {
        return new AtomCacheRef(stream.read(1)[0]);
    }

    public static Byte decodeSmallIntegerExt(StreamInterface stream) throws IOException {
        return stream.read(1)[0];
    }

    public static Integer decodeIntegerExt(StreamInterface stream) throws IOException {
        return unpackNumber(stream.read(4)).intValue();
    }

    public static Double decodeFloatExt(StreamInterface stream) throws IOException {
        return new Double(new String(stream.read(31)));
    }

    public static Atom decodeAtomExt(StreamInterface stream) throws IOException {
        Integer atom_len = unpackNumber(stream.read(2)).intValue();
        return new Atom(new String(stream.read(atom_len)));
    }

    public static Reference decodeReferenceExt(StreamInterface stream) throws IOException {
        Atom atom = (Atom) decode(stream, false);
        Integer identifier = unpackNumber(stream.read(4)).intValue();
        Byte creation = stream.read(1)[0];
        return new Reference(atom, identifier, creation);
    }

    public static Port decodePortExt(StreamInterface stream) throws IOException {
        Atom atom = (Atom) decode(stream, false);
        Integer identifier = unpackNumber(stream.read(4)).intValue();
        Byte creation = stream.read(1)[0];
        return new Port(atom, identifier, creation);
    }

    public static Pid decodePidExt(StreamInterface stream) throws IOException {
        Atom atom = (Atom) decode(stream, false);
        Integer identifier = unpackNumber(stream.read(4)).intValue();
        Integer serial = unpackNumber(stream.read(4)).intValue();
        Byte creation = stream.read(1)[0];
        return new Pid(atom, identifier, serial, creation);
    }

    public static Tuple decodeSmallTupleExt(StreamInterface stream) throws IOException {
        Short tupleLen = unpackNumber(stream.read(1)).shortValue();
        Tuple tuple = new Tuple(tupleLen.intValue());
        Object element;
        for (int i = 0; i < tupleLen; i++) {
            element = decode(stream, false);
            tuple.mElements.add(element);
        }
        return tuple;
    }

    public static Tuple decodeLargeTupleExt(StreamInterface stream) throws IOException {
        Integer tupleLen = unpackNumber(stream.read(4)).intValue();
        Tuple tuple = new Tuple(tupleLen);
        Object element;
        for (int i = 0; i < tupleLen; i++) {
            element = decode(stream, false);
            tuple.mElements.add(element);
        }
        return tuple;
    }

    public static Nil decodeNilExt(StreamInterface stream) {
        return new Nil();
    }

    public static String decodeStringExt(StreamInterface stream) throws IOException {
        Integer strLen = unpackNumber(stream.read(2)).intValue();
        return new String(stream.read(strLen));
    }

    public static Object decodeListExt(StreamInterface stream) throws IOException {
        Integer listLen = unpackNumber(stream.read(4)).intValue();
        ArrayList<Object> list = new ArrayList<Object>(listLen);
        Object value;
        Boolean isStr = true;
        for (Integer i = 0; i < listLen; i++) {
            value = decode(stream, false);
            isStr = isStr && (value instanceof Byte);
            list.add(value);
        }
        Object tail = decode(stream, false);
        if (!(tail instanceof Nil)) {
            isStr = isStr && (tail instanceof Byte);
            list.add(tail);
        }
        if (isStr) {
            StringBuilder builder = new StringBuilder();
            for (Integer i = 0; i < list.size(); i++) {
                builder.append(list.get(i));
            }
            return builder.toString();
        } else {
            return list;
        }
    }

    public static Binary decodeBinaryExt(StreamInterface stream) throws IOException {
        Integer binLen = unpackNumber(stream.read(4)).intValue();
        return new Binary(stream.read(binLen));
    }

    public static BigInteger decodeSmallBigExt(StreamInterface stream) throws IOException {
        BigInteger value = new BigInteger("0");
        Short numBytes = unpackNumber(stream.read(1)).shortValue();
        Byte sign = unpackNumber(stream.read(1)).byteValue();
        Integer current;
        BigInteger plus;
        for (Integer i = 0; i < numBytes; i++) {
            current = unpackNumber(stream.read(1)).intValue();
            plus = new BigInteger(current.toString()).multiply(new BigInteger("256").pow(i));
            value = value.add(plus);
        }
        if (sign == 1) {
            value = value.multiply(new BigInteger(new String("-1")));
        }
        return value;
    }

    public static BigInteger decodeLargeBigExt(StreamInterface stream) throws IOException {
        BigInteger value = new BigInteger("0");
        Long numBytes = unpackNumber(stream.read(4));
        Byte sign = unpackNumber(stream.read(1)).byteValue();
        Integer current;
        BigInteger plus;
        for (Integer i = 0; i < numBytes; i++) {
            current = unpackNumber(stream.read(1)).intValue();
            plus = new BigInteger(current.toString()).multiply(new BigInteger("256").pow(i));
            value = value.add(plus);
        }
        if (sign == 1) {
            value = value.multiply(new BigInteger(new String("-1")));
        }
        return value;
    }

    public static NewFunction decodeNewFunExt(StreamInterface stream) throws IOException {
        Long size = unpackNumber(stream.read(4));
        Byte arity = unpackNumber(stream.read(1)).byteValue();
        String uniq = new String(stream.read(16));
        Integer index = unpackNumber(stream.read(4)).intValue();
        Long numFree = unpackNumber(stream.read(4));
        Object module = decode(stream, false);
        Object oldIndex = decode(stream, false);
        Object oldUniq = decode(stream, false);
        Pid pid = (Pid) decode(stream, false);
        ArrayList<Object> freeVars = new ArrayList<Object>();

        Object freeVar;
        for (Integer i = 0; i < numFree; i++) {
            freeVar = decode(stream, false);
            freeVars.add(freeVar);
        }

        return new NewFunction(
            arity, uniq, index, module, oldIndex, oldUniq, pid, freeVars
        );
    }

    public static Atom decodeSmallAtomExt(StreamInterface stream) throws IOException {
        Integer atomLen = unpackNumber(stream.read(1)).intValue();
        String atomName = new String(stream.read(atomLen));
        return new Atom(atomName);
    }

    public static ErlFunction decodeFunExt(StreamInterface stream) throws IOException {
        Long numFree = unpackNumber(stream.read(4));
        Pid pid = (Pid) decode(stream, false);
        Object module = decode(stream, false);
        Object index = decode(stream, false);
        Object uniq = decode(stream, false);
        ArrayList<Object> freeVars = new ArrayList<Object>();

        Object freeVar;
        for (Integer i = 0; i < numFree; i++) {
            freeVar = decode(stream, false);
            freeVars.add(freeVar);
        }

        return new ErlFunction(
            pid, module, index, uniq, freeVars
        );
    }

    public static Export decodeExportExt(StreamInterface stream) throws IOException {
        Object module = decode(stream, false);
        Object function = decode(stream, false);
        Byte arity = (Byte) decode(stream, false);

        return new Export(module, function, arity);
    }

    public static NewReference decodeNewReferenceExt(StreamInterface stream) throws IOException {
        Integer length = unpackNumber(stream.read(2)).intValue();
        Object atom = decode(stream, false);
        Byte creation = unpackNumber(stream.read(1)).byteValue();
        Integer[] revIds = new Integer[length];
        for (Integer i = 0; i < length; i++) {
            revIds[i] = unpackNumber(stream.read(4)).intValue();
        }
        ArrayList<Integer> identifiers = new ArrayList<Integer>(length);
        for (Integer i = length - 1; i >= 0; i--) {
            identifiers.add(revIds[i]);
        }
        return new NewReference(atom, creation, identifiers);
    }

    public static BitBinary decodeBitBinaryExt(StreamInterface stream) throws IOException {
        Integer length = unpackNumber(stream.read(4)).intValue();
        return new BitBinary(
            unpackNumber(stream.read(1)).byteValue(),
            stream.read(length)
        );
    }

    public static Double decodeNewFloatExt(StreamInterface stream) throws IOException {
        Long value = unpackNumber(stream.read(8));
        return Double.longBitsToDouble(value);
    }

    public static Object decode(StreamInterface stream) throws UnsupportedOperationException, IOException {
        return decode(stream, true);
    }

    public static Object decode(StreamInterface stream, Boolean checkDistTag) throws UnsupportedOperationException, IOException {
        byte firstByte = stream.read(1)[0];
        byte extCode;
        if (checkDistTag) {
            if (firstByte != (byte) 131) {
                throw new UnsupportedOperationException("this is not an Erlang EXT datatype");
            } else {
                extCode = stream.read(1)[0];
            }
        } else {
            extCode = firstByte;
        }

        switch (extCode) {
            case 70:  return decodeNewFloatExt(stream);
            case 77:  return decodeBitBinaryExt(stream);
            case 82:  return decodeAtomCacheRef(stream);
            case 97:  return decodeSmallIntegerExt(stream);
            case 98:  return decodeIntegerExt(stream);
            case 99:  return decodeFloatExt(stream);
            case 100: return decodeAtomExt(stream);
            case 101: return decodeReferenceExt(stream);
            case 102: return decodePortExt(stream);
            case 103: return decodePidExt(stream);
            case 104: return decodeSmallTupleExt(stream);
            case 105: return decodeLargeTupleExt(stream);
            case 106: return decodeNilExt(stream);
            case 107: return decodeStringExt(stream);
            case 108: return decodeListExt(stream);
            case 109: return decodeBinaryExt(stream);
            case 110: return decodeSmallBigExt(stream);
            case 111: return decodeLargeBigExt(stream);
            case 112: return decodeNewFunExt(stream);
            case 113: return decodeExportExt(stream);
            case 114: return decodeNewReferenceExt(stream);
            case 115: return decodeSmallAtomExt(stream);
            case 117: return decodeFunExt(stream);
            default:
                throw new UnsupportedOperationException(
                    "Unable to decode Erlang EXT data type: " + extCode
                );
        }
    }
}