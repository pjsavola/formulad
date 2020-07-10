package formulad.model;

import java.io.Serializable;

public enum TypeEnum implements Serializable {
    START("START"),
    STRAIGHT("STRAIGHT"),
    CURVE_1("CURVE_1"),
    CURVE_2("CURVE_2"),
    CURVE_3("CURVE_3"),
    FINISH("FINISH");

    private String value;

    TypeEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

    public static TypeEnum fromValue(String text) {
        for (TypeEnum b : TypeEnum.values()) {
            if (String.valueOf(b.value).equals(text)) {
                return b;
            }
        }
        return null;
    }
}
