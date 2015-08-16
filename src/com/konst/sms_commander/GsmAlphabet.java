/*
 * Copyright (c) 2015.
 */

package com.konst.sms_commander;

import android.content.Context;
import android.content.Intent;
import android.telephony.PhoneNumberUtils;
import android.util.SparseIntArray;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * This class implements the character set mapping between
 * the GSM SMS 7-bit alphabet specified in TS 23.038 6.2.1
 * and UTF-16
 *
 */
public class GsmAlphabet {
    private static final String TAG = "GSM";

    /**
     * Входящее сообщение.
     */
    private static final String SMS_RECEIVED_ACTION = "android.provider.Telephony.SMS_RECEIVED";

    /**
     * This escapes extended characters, and when present indicates that the
     * following character should be looked up in the "extended" table.
     *
     * gsmToChar(GSM_EXTENDED_ESCAPE) returns 0xffff
     */
    private static final byte GSM_EXTENDED_ESCAPE = 0x1B;

    /**
     * Converts a String into a byte array containing
     * the 7-bit packed GSM Alphabet representation of the string.
     *
     * Unencodable chars are encoded as spaces
     *
     * Byte 0 in the returned byte array is the count of septets used
     * The returned byte array is the minimum size required to store
     * the packed septets. The returned array cannot contain more than 255
     * septets.
     *
     * @param data the data string to encode
     * @return the encoded string
     * @throws Exception if String is too large to encode
     */
    public static byte[] stringToGsm7BitPacked(String data) throws Exception {
        return stringToGsm7BitPacked(data, 0, true, 0, 0);
    }

    /**
     * Converts a String into a byte array containing
     * the 7-bit packed GSM Alphabet representation of the string.
     *
     * Byte 0 in the returned byte array is the count of septets used
     * The returned byte array is the minimum size required to store
     * the packed septets. The returned array cannot contain more than 255
     * septets.
     *
     * @param data the text to convert to septets
     * @param startingSeptetOffset the number of padding septets to put before
     *  the character data at the beginning of the array
     * @param throwException If true, throws EncodeException on invalid char.
     *   If false, replaces unencodable char with GSM alphabet space char.
     * @param languageTable the 7 bit language table, or 0 for the default GSM alphabet
     * @param languageShiftTable the 7 bit single shift language table, or 0 for the default
     *     GSM extension table
     * @return the encoded message
     *
     * @throws Exception if String is too large to encode
     */
    private static byte[] stringToGsm7BitPacked(CharSequence data, int startingSeptetOffset,
            boolean throwException, int languageTable, int languageShiftTable)
            throws Exception {
        int dataLen = data.length();
        int septetCount = countGsmSeptetsUsingTables(data, !throwException, languageTable, languageShiftTable);
        if (septetCount == -1) {
            throw new Exception("countGsmSeptetsUsingTables(): unencodable char");
        }
        septetCount += startingSeptetOffset;
        if (septetCount > 255) {
            throw new Exception("Payload cannot exceed 255 septets");
        }
        int byteCount = (septetCount * 7 + 7) / 8;
        byte[] ret = new byte[byteCount + 1];  // Include space for one byte length prefix.
        SparseIntArray charToLanguageTable = sCharsToGsmTables[languageTable];
        SparseIntArray charToShiftTable = sCharsToShiftTables[languageShiftTable];
        for (int i = 0, septets = startingSeptetOffset, bitOffset = startingSeptetOffset * 7;
                 i < dataLen && septets < septetCount;
                 i++, bitOffset += 7) {
            char c = data.charAt(i);
            int v = charToLanguageTable.get(c, -1);
            if (v == -1) {
                v = charToShiftTable.get(c, -1);  // Lookup the extended char.
                if (v == -1) {
                    if (throwException) {
                        throw new Exception("stringToGsm7BitPacked(): unencodable char");
                    } else {
                        v = charToLanguageTable.get(' ', ' ');   // should return ASCII space
                    }
                } else {
                    packSmsChar(ret, bitOffset, GSM_EXTENDED_ESCAPE);
                    bitOffset += 7;
                    septets++;
                }
            }
            packSmsChar(ret, bitOffset, v);
            septets++;
        }
        ret[0] = (byte) septetCount;  // Validated by check above.
        return ret;
    }

    /**
     * Pack a 7-bit char into its appropriate place in a byte array
     *
     * @param packedChars the destination byte array
     * @param bitOffset the bit offset that the septet should be packed at
     *                  (septet index * 7)
     * @param value the 7-bit character to store
     */
    private static void packSmsChar(byte[] packedChars, int bitOffset, int value) {
        int byteOffset = bitOffset / 8;
        int shift = bitOffset % 8;

        packedChars[++byteOffset] |= value << shift;

        if (shift > 1) {
            packedChars[++byteOffset] = (byte)(value >> 8 - shift);
        }
    }

    /**
     * Returns the count of 7-bit GSM alphabet characters needed
     * to represent this string, using the specified 7-bit language table
     * and extension table (0 for GSM default tables).
     * @param s the Unicode string that will be encoded
     * @param use7bitOnly allow using space in place of unencodable character if true,
     *     otherwise, return -1 if any characters are unencodable
     * @param languageTable the 7 bit language table, or 0 for the default GSM alphabet
     * @param languageShiftTable the 7 bit single shift language table, or 0 for the default
     *     GSM extension table
     * @return the septet count for s using the specified language tables, or -1 if any
     *     characters are unencodable and use7bitOnly is false
     */
    private static int countGsmSeptetsUsingTables(CharSequence s, boolean use7bitOnly, int languageTable, int languageShiftTable) {
        int count = 0;
        int sz = s.length();
        SparseIntArray charToLanguageTable = sCharsToGsmTables[languageTable];
        SparseIntArray charToShiftTable = sCharsToShiftTables[languageShiftTable];
        for (int i = 0; i < sz; i++) {
            char c = s.charAt(i);
            if (c == GSM_EXTENDED_ESCAPE) {
                Log.w(TAG, "countGsmSeptets() string contains Escape character, skipping.");
                continue;
            }
            if (charToLanguageTable.get(c, -1) != -1) {
                count++;
            } else if (charToShiftTable.get(c, -1) != -1) {
                count += 2; // escape + shift table index
            } else if (use7bitOnly) {
                count++;    // encode as space
            } else {
                return -1;  // caller must check for this case
            }
        }
        return count;
    }

    public static void createFakeSms(Context context, String sender, String body) {
        //byte[] pdu = null;
        byte[] scBytes = PhoneNumberUtils.networkPortionToCalledPartyBCD("380500000000");

        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            bo.write(scBytes.length);
            bo.write(scBytes);
            bo.write(0x04);
            bo.write((byte) sender.length());
            //bo.write(0x91);
            bo.write(PhoneNumberUtils.networkPortionToCalledPartyBCD(sender));
            bo.write(0x00);
            bo.write(0x00); // encoding: 0 for default 7bit
            bo.write(createDate());
            bo.write(stringToGsm7BitPacked(body));
            //pdu = bo.toByteArray();
            Intent intent = new Intent(SMS_RECEIVED_ACTION);
            intent.putExtra("pdus", new Object[]{bo.toByteArray()});
            intent.putExtra("format", "3gpp");
            context.sendBroadcast(intent);

        } catch (IOException e) {
        } catch (Exception e) { }
    }

    private static byte[] createDate(){
        byte[] dateBytes = new byte[7];
        Calendar calendar = new GregorianCalendar();
        dateBytes[0] = reverseByte((byte) (calendar.get(Calendar.YEAR)));
        dateBytes[1] = reverseByte((byte) (calendar.get(Calendar.MONTH)));
        dateBytes[2] = reverseByte((byte) (calendar.get(Calendar.DAY_OF_MONTH)));
        dateBytes[3] = reverseByte((byte) (calendar.get(Calendar.HOUR_OF_DAY)));
        dateBytes[4] = reverseByte((byte) (calendar.get(Calendar.MINUTE)));
        dateBytes[5] = reverseByte((byte) (calendar.get(Calendar.SECOND)));
        dateBytes[6] = reverseByte((byte) (calendar.get(Calendar.ZONE_OFFSET)));
        //dateBytes[6] = reverseByte((byte) ((calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET)) / (60 * 1000 * 15)));
        return dateBytes;
    }

    private static byte reverseByte(byte b) {
        return (byte) ((b & 0xF0) >> 4 | (b & 0x0F) << 4);
    }

    /** Reverse mapping from Unicode characters to indexes into language tables. */
    private static final SparseIntArray[] sCharsToGsmTables;

    /** Reverse mapping from Unicode characters to indexes into language shift tables. */
    private static final SparseIntArray[] sCharsToShiftTables;

    /**
     * GSM default 7 bit alphabet plus national language locking shift character tables.
     * Comment lines above strings indicate the lower four bits of the table position.
     */
    private static final String[] sLanguageTables = {
        /* 3GPP TS 23.038 V9.1.1 section 6.2.1 - GSM 7 bit Default Alphabet
         01.....23.....4.....5.....6.....7.....8.....9.....A.B.....C.....D.E.....F.....0.....1 */
        "@\u00a3$\u00a5\u00e8\u00e9\u00f9\u00ec\u00f2\u00c7\n\u00d8\u00f8\r\u00c5\u00e5\u0394_"
            // 2.....3.....4.....5.....6.....7.....8.....9.....A.....B.....C.....D.....E.....
            + "\u03a6\u0393\u039b\u03a9\u03a0\u03a8\u03a3\u0398\u039e\uffff\u00c6\u00e6\u00df"
            // F.....012.34.....56789ABCDEF0123456789ABCDEF0.....123456789ABCDEF0123456789A
            + "\u00c9 !\"#\u00a4%&'()*+,-./0123456789:;<=>?\u00a1ABCDEFGHIJKLMNOPQRSTUVWXYZ"
            // B.....C.....D.....E.....F.....0.....123456789ABCDEF0123456789AB.....C.....D.....
            + "\u00c4\u00d6\u00d1\u00dc\u00a7\u00bfabcdefghijklmnopqrstuvwxyz\u00e4\u00f6\u00f1"
            // E.....F.....
            + "\u00fc\u00e0"

    };

    /**
     * GSM default extension table plus national language single shift character tables.
     */
    private static final String[] sLanguageShiftTables = {
        /* 6.2.1.1 GSM 7 bit Default Alphabet Extension Table
         0123456789A.....BCDEF0123456789ABCDEF0123456789ABCDEF.0123456789ABCDEF0123456789ABCDEF */
        "          \u000c         ^                   {}     \\            [~] |               "
            // 0123456789ABCDEF012345.....6789ABCDEF0123456789ABCDEF
            + "                     \u20ac                          "

    };

    static {
        int numTables = sLanguageTables.length;
        int numShiftTables = sLanguageShiftTables.length;
        if (numTables != numShiftTables) {
            Log.e(TAG, "Error: language tables array length " + numTables +
                    " != shift tables array length " + numShiftTables);
        }

        sCharsToGsmTables = new SparseIntArray[numTables];
        for (int i = 0; i < numTables; i++) {
            String table = sLanguageTables[i];

            int tableLen = table.length();
            if (tableLen != 0 && tableLen != 128) {
                Log.e(TAG, "Error: language tables index " + i + " length " + tableLen + " (expected 128 or 0)");
            }

            SparseIntArray charToGsmTable = new SparseIntArray(tableLen);
            sCharsToGsmTables[i] = charToGsmTable;
            for (int j = 0; j < tableLen; j++) {
                char c = table.charAt(j);
                charToGsmTable.put(c, j);
            }
        }

        sCharsToShiftTables = new SparseIntArray[numTables];
        for (int i = 0; i < numShiftTables; i++) {
            String shiftTable = sLanguageShiftTables[i];

            int shiftTableLen = shiftTable.length();
            if (shiftTableLen != 0 && shiftTableLen != 128) {
                Log.e(TAG, "Error: language shift tables index " + i + " length " + shiftTableLen + " (expected 128 or 0)");
            }

            SparseIntArray charToShiftTable = new SparseIntArray(shiftTableLen);
            sCharsToShiftTables[i] = charToShiftTable;
            for (int j = 0; j < shiftTableLen; j++) {
                char c = shiftTable.charAt(j);
                if (c != ' ') {
                    charToShiftTable.put(c, j);
                }
            }
        }
    }
}
