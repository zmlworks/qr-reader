package com.zmlworks.qrreader;

import java.util.Locale;

/** Display names of barcode formats (pure Java, unit-tested). QR codes map to null: they need no label. */
public final class Formats {
    private Formats() {}

    /** @param name a zxing-cpp {@code BarcodeReader.Format} or ZXing {@code BarcodeFormat} enum name */
    public static String label(String name) {
        if (name == null) return null;
        switch (name) {
            case "QR_CODE": case "QR_CODE_MODEL_1": case "QR_CODE_MODEL_2": return null;
            case "MICRO_QR_CODE": return "Micro QR";
            case "RMQR_CODE": return "rMQR";
            case "EAN_13": return "EAN-13";
            case "EAN_8": return "EAN-8";
            case "EAN_5": return "EAN-5";
            case "EAN_2": return "EAN-2";
            case "UPC_A": return "UPC-A";
            case "UPC_E": return "UPC-E";
            case "UPC_EAN_EXTENSION": return "EAN add-on";
            case "ISBN": return "ISBN";
            case "CODE_128": return "Code 128";
            case "CODE_39": case "CODE_39_STD": case "CODE_39_EXT": return "Code 39";
            case "CODE_32": return "Code 32";
            case "CODE_93": return "Code 93";
            case "PZN": return "PZN";
            case "ITF": return "ITF";
            case "ITF_14": return "ITF-14";
            case "CODABAR": return "Codabar";
            case "DATA_MATRIX": return "DataMatrix";
            case "AZTEC": case "AZTEC_CODE": return "Aztec";
            case "AZTEC_RUNE": return "Aztec Rune";
            case "PDF_417": case "COMPACT_PDF_417": return "PDF417";
            case "MICRO_PDF_417": return "MicroPDF417";
            case "MAXI_CODE": case "MAXICODE": return "MaxiCode";
            case "RSS_14": case "DATA_BAR": case "DATA_BAR_OMNI": return "DataBar";
            case "DATA_BAR_STK": case "DATA_BAR_STK_OMNI": return "DataBar Stacked";
            case "DATA_BAR_LTD": return "DataBar Limited";
            case "RSS_EXPANDED": case "DATA_BAR_EXP": case "DATA_BAR_EXP_STK": return "DataBar Expanded";
            case "TELEPEN": case "TELEPEN_ALPHA": case "TELEPEN_NUMERIC": return "Telepen";
            case "DX_FILM_EDGE": return "DX Film Edge";
            default: {   // future formats: "SOME_NEW_CODE" -> "Some New Code"
                StringBuilder b = new StringBuilder();
                for (String w : name.toLowerCase(Locale.ROOT).split("_")) {
                    if (w.isEmpty()) continue;
                    if (b.length() > 0) b.append(' ');
                    b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
                }
                return b.length() == 0 ? null : b.toString();
            }
        }
    }
}
