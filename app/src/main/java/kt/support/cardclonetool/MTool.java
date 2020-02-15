package kt.support.cardclonetool;

import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

class MTool {

    final private DataHelper dataHelper = new DataHelper();
    private MifareClassic mifareClassic;

    MTool(Tag tag) throws IOException {
        mifareClassic = MifareClassic.get(tag);
        mifareClassic.connect();
    }

    boolean isNull() {
        return mifareClassic == null;
    }

    // 读取0块数据
    String getBlock0() throws IOException {
        mifareClassic.authenticateSectorWithKeyA(0, MifareClassic.KEY_DEFAULT);
        return dataHelper.byte2HexString(mifareClassic.readBlock(mifareClassic.sectorToBlock(0)));
    }

    // 写入块数据
    void writeBlock(int selectorIndex, int blockIndex, String data) throws IOException {
        mifareClassic.authenticateSectorWithKeyA(selectorIndex, MifareClassic.KEY_DEFAULT);
        if (data.length() == 8)
            mifareClassic.writeBlock(mifareClassic.sectorToBlock(selectorIndex) + blockIndex, dataHelper.stringToByte16(data));
        else
            mifareClassic.writeBlock(mifareClassic.sectorToBlock(selectorIndex) + blockIndex, dataHelper.hexStringToByteArray(data));
    }

    // 获取学号
    String getStudentID() throws IOException {
        if (mifareClassic.authenticateSectorWithKeyA(11, dataHelper.hexStringToByteArray("9E9667426E50")))
            // 读取学号加密扇区
            return new String(mifareClassic.readBlock(mifareClassic.sectorToBlock(11) + 2), StandardCharsets.US_ASCII).substring(0, 8);
        else
            return null;
    }

    private class DataHelper {

        String byte2HexString(byte[] bytes) {
            StringBuilder ret = new StringBuilder();
            if (bytes != null) {
                for (Byte b : bytes) {
                    ret.append(String.format("%02X", b.intValue() & 0xFF));
                }
            }
            return ret.toString();
        }

        byte[] hexStringToByteArray(String s) {
            int len = s.length();
            byte[] data = new byte[len / 2];
            try {
                for (int i = 0; i < len; i += 2) {
                    data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                            + Character.digit(s.charAt(i+1), 16));
                }
            } catch (Exception ignored) {}
            return data;
        }

        String hexToAscii(String hexStr) {
            StringBuilder output = new StringBuilder();
            for (int i = 0; i < hexStr.length(); i += 2) {
                String str = hexStr.substring(i, i + 2);
                output.append((char) Integer.parseInt(str, 16));
            }
            return output.toString();
        }

        // 固定 16 字节长度
        byte[] stringToByte16(String data) {
            byte[] origin = data.getBytes();
            byte[] result = new byte[16];
            System.arraycopy(origin, 0, result, 0, origin.length);
            System.arraycopy(new byte[16 - origin.length], 0, result, origin.length, 16 - origin.length);
            return result;
        }
    }

    public void close() {
        try {
            mifareClassic.close();
        }
        catch (IOException e) {
            Log.d(MTool.class.getSimpleName(), "Error on closing tag.");
        }
    }
}
