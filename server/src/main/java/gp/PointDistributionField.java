package gp;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.stream.Collectors;

public class PointDistributionField extends JPanel {
    private final JPanel parent;
    private final String name;
    private final JTextField textField;

    PointDistributionField(JPanel parent, String name, int[] distribution) {
        super(new FlowLayout(FlowLayout.LEFT));
        final JLabel label = new JLabel(name);
        final JTextField field = new JTextField(distToString(distribution));
        field.setPreferredSize(new Dimension(200, 20));
        add(label);
        add(field);
        this.parent = parent;
        this.name = name;
        this.textField = field;
    }

    static String distToString(int[] distribution) {
        return Arrays.stream(distribution).mapToObj(Integer::toString).collect(Collectors.joining("-"));
    }

    static int[] stringToDist(String distribution) {
        return Arrays.stream(distribution.split("-")).mapToInt(Integer::parseInt).toArray();
    }

    public int[] getValue() throws NumberFormatException {
        int result[];
        try {
            final String txt = textField.getText();
            if (txt == null || txt.isEmpty()) {
                throw new RuntimeException("Empty field");
            }
            result = stringToDist(txt);
        } catch (Exception e) {
            JOptionPane.showConfirmDialog(parent, "Invalid value for " + name + ": " + textField.getText(), "Error", JOptionPane.DEFAULT_OPTION);
            throw e;
        }
        return result;
    }
}
