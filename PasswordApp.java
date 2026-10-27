import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.IntSupplier;

public class PasswordApp extends JFrame {
    private static final String FONT = "Tahoma";
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private String password = "";

    private JSpinner lengthSpinner;
    private JTextField setupField;
    private JLabel setupMsg;
    private JPasswordField loginField;
    private JCheckBox showBox;
    private JLabel loginHint, loginMsg;
    private int attempts = 0;
    private Confetti confetti;

    public PasswordApp() {
        super("ระบบตั้งรหัสผ่าน");
        root.add(buildSetup(), "setup");
        root.add(buildLogin(), "login");
        root.add(buildSuccess(), "success");
        setContentPane(root);
        setSize(480, 400);
        setResizable(false);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        goSetup();
    }

    /** ยอมรับเฉพาะ a-z, 0-9 และไม่เกินความยาวที่กำหนด */
    static class PwFilter extends DocumentFilter {
        private final IntSupplier max;
        PwFilter(IntSupplier max) { this.max = max; }

        private boolean ok(String s) { return s.matches("[a-z0-9]*") && s.length() <= max.getAsInt(); }

        @Override
        public void insertString(FilterBypass fb, int off, String str, AttributeSet a) throws BadLocationException {
            replace(fb, off, 0, str, a);
        }

        @Override
        public void replace(FilterBypass fb, int off, int len, String str, AttributeSet a) throws BadLocationException {
            String cur = fb.getDocument().getText(0, fb.getDocument().getLength());
            String next = cur.substring(0, off) + (str == null ? "" : str) + cur.substring(off + len);
            if (ok(next)) super.replace(fb, off, len, str, a);
        }
    }

    private JLabel label(String text, int size, int style, Color c) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(new Font(FONT, style, size));
        l.setForeground(c);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    private JPanel column(Color bg) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(bg);
        p.setBorder(BorderFactory.createEmptyBorder(30, 20, 20, 20));
        return p;
    }

    // ---------- หน้า 1: ตั้งรหัส ----------
    private JPanel buildSetup() {
        JPanel p = column(new Color(0xF8, 0xFA, 0xFC));
        p.add(label("ตั้งรหัสผ่าน", 24, Font.BOLD, Color.BLACK));
        p.add(Box.createVerticalStrut(15));

        JPanel row = new JPanel();
        row.setOpaque(false);
        row.add(label("จำนวนตัวอักษร (1-10):", 14, Font.PLAIN, Color.BLACK));
        lengthSpinner = new JSpinner(new SpinnerNumberModel(6, 1, 10, 1));
        ((JSpinner.DefaultEditor) lengthSpinner.getEditor()).getTextField().setEditable(false);
        row.add(lengthSpinner);
        p.add(row);

        p.add(label("ใช้ได้เฉพาะ a-z (ตัวพิมพ์เล็ก) และ 0-9", 12, Font.PLAIN, Color.GRAY));
        p.add(Box.createVerticalStrut(12));

        setupField = new JTextField(14);
        setupField.setFont(new Font("Monospaced", Font.PLAIN, 20));
        setupField.setHorizontalAlignment(JTextField.CENTER);
        setupField.setMaximumSize(new Dimension(260, 40));
        ((AbstractDocument) setupField.getDocument())
                .setDocumentFilter(new PwFilter(() -> (Integer) lengthSpinner.getValue()));
        setupField.addActionListener(e -> confirmSetup());
        p.add(setupField);

        lengthSpinner.addChangeListener(e -> {
            int n = (Integer) lengthSpinner.getValue();
            String t = setupField.getText();
            if (t.length() > n) setupField.setText(t.substring(0, n));
        });

        p.add(Box.createVerticalStrut(10));
        setupMsg = label(" ", 13, Font.PLAIN, new Color(0xDC, 0x26, 0x26));
        p.add(setupMsg);
        p.add(Box.createVerticalStrut(10));

        JButton ok = new JButton("ยืนยันรหัส");
        ok.setFont(new Font(FONT, Font.PLAIN, 14));
        ok.setAlignmentX(Component.CENTER_ALIGNMENT);
        ok.addActionListener(e -> confirmSetup());
        p.add(ok);
        return p;
    }

    private void goSetup() {
        setupField.setText("");
        setupMsg.setText(" ");
        cards.show(root, "setup");
        setupField.requestFocusInWindow();
    }

    private void confirmSetup() {
        int n = (Integer) lengthSpinner.getValue();
        String pw = setupField.getText();
        if (pw.length() != n) {
            setupMsg.setText("ต้องใส่ให้ครบ " + n + " ตัวอักษร (ตอนนี้ " + pw.length() + ")");
            return;
        }
        password = pw;
        goLogin();
    }

    // ---------- หน้า 2: กรอกรหัส ----------
    private JPanel buildLogin() {
        JPanel p = column(new Color(0xF1, 0xF5, 0xF9));
        p.setBorder(BorderFactory.createEmptyBorder(40, 20, 20, 20));
        p.add(label("กรอกรหัสผ่าน", 24, Font.BOLD, Color.BLACK));
        p.add(Box.createVerticalStrut(8));
        loginHint = label(" ", 13, Font.PLAIN, Color.GRAY);
        p.add(loginHint);
        p.add(Box.createVerticalStrut(14));

        loginField = new JPasswordField(14);
        loginField.setFont(new Font("Monospaced", Font.PLAIN, 20));
        loginField.setHorizontalAlignment(JTextField.CENTER);
        loginField.setMaximumSize(new Dimension(260, 40));
        ((AbstractDocument) loginField.getDocument())
                .setDocumentFilter(new PwFilter(() -> password.length()));
        // พิมพ์ครบจำนวนแล้วตรวจให้เองอัตโนมัติ (หน่วงนิดเดียวให้เห็นตัวสุดท้าย)
        loginField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void changed() {
                if (!password.isEmpty() && loginField.getPassword().length == password.length()) {
                    Timer t = new Timer(150, e -> checkLogin());
                    t.setRepeats(false);
                    t.start();
                }
            }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { }
        });
        p.add(loginField);

        showBox = new JCheckBox("แสดงรหัส");
        showBox.setFont(new Font(FONT, Font.PLAIN, 12));
        showBox.setOpaque(false);
        showBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        char echo = loginField.getEchoChar();
        showBox.addActionListener(e -> loginField.setEchoChar(showBox.isSelected() ? (char) 0 : echo));
        p.add(showBox);

        p.add(Box.createVerticalStrut(8));
        loginMsg = label(" ", 13, Font.PLAIN, new Color(0xDC, 0x26, 0x26));
        p.add(loginMsg);
        p.add(Box.createVerticalStrut(10));

        JButton back = new JButton("กลับไปตั้งรหัสใหม่");
        back.setFont(new Font(FONT, Font.PLAIN, 12));
        back.setAlignmentX(Component.CENTER_ALIGNMENT);
        back.addActionListener(e -> goSetup());
        p.add(back);
        return p;
    }

    private void goLogin() {
        attempts = 0;
        loginField.setText("");
        loginMsg.setText(" ");
        loginHint.setText("รหัสมีทั้งหมด " + password.length() + " ตัวอักษร");
        cards.show(root, "login");
        loginField.requestFocusInWindow();
    }

    private void checkLogin() {
        if (loginField.getPassword().length != password.length()) return;
        if (new String(loginField.getPassword()).equals(password)) {
            confetti.start();
            cards.show(root, "success");
        } else {
            attempts++;
            loginMsg.setText("รหัสไม่ถูกต้อง ลองอีกครั้ง (ครั้งที่ " + attempts + ")");
            loginField.setText("");
        }
    }

    // ---------- หน้า 3: สำเร็จ ----------
    private JPanel buildSuccess() {
        JPanel p = new JPanel(new BorderLayout());
        confetti = new Confetti();
        p.add(confetti, BorderLayout.CENTER);

        JPanel btns = new JPanel();
        btns.setBackground(new Color(0xFF, 0xFB, 0xEB));
        JButton again = new JButton("ตั้งรหัสใหม่");
        JButton quit = new JButton("ออก");
        again.setFont(new Font(FONT, Font.PLAIN, 14));
        quit.setFont(new Font(FONT, Font.PLAIN, 14));
        again.addActionListener(e -> { confetti.stop(); goSetup(); });
        quit.addActionListener(e -> System.exit(0));
        btns.add(again);
        btns.add(quit);
        p.add(btns, BorderLayout.SOUTH);
        return p;
    }

    static class Confetti extends JPanel {
        private static final Color[] COLORS = {
            new Color(0xEF4444), new Color(0xF59E0B), new Color(0x10B981),
            new Color(0x3B82F6), new Color(0x8B5CF6), new Color(0xEC4899), new Color(0xFACC15)
        };
        private static class P { double x, y, vx, vy; int s; Color c; }
        private final List<P> ps = new ArrayList<>();
        private final Random r = new Random();
        private final Timer timer;

        Confetti() {
            setBackground(new Color(0xFF, 0xFB, 0xEB));
            timer = new Timer(30, e -> { step(); repaint(); });
        }

        void start() { ps.clear(); timer.start(); }
        void stop() { timer.stop(); }

        private void step() {
            for (int i = 0; i < 3; i++) {
                P p = new P();
                p.x = r.nextInt(Math.max(1, getWidth()));
                p.y = -10;
                p.vx = r.nextDouble() * 3 - 1.5;
                p.vy = 2 + r.nextDouble() * 3;
                p.s = 6 + r.nextInt(7);
                p.c = COLORS[r.nextInt(COLORS.length)];
                ps.add(p);
            }
            Iterator<P> it = ps.iterator();
            while (it.hasNext()) {
                P p = it.next();
                p.x += p.vx;
                p.y += p.vy;
                if (p.y > getHeight()) it.remove();
            }
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            for (P p : ps) {
                g.setColor(p.c);
                g.fillRect((int) p.x, (int) p.y, p.s, p.s);
            }
            g.setColor(new Color(0x16A34A));
            g.setFont(new Font(FONT, Font.BOLD, 44));
            drawCentered(g, "ถูกต้อง!", getHeight() / 2 - 10);
            g.setColor(new Color(0x334155));
            g.setFont(new Font(FONT, Font.PLAIN, 16));
            drawCentered(g, "ยินดีด้วย รหัสผ่านของคุณถูกต้อง", getHeight() / 2 + 35);
        }

        private void drawCentered(Graphics2D g, String s, int y) {
            int w = g.getFontMetrics().stringWidth(s);
            g.drawString(s, (getWidth() - w) / 2, y);
        }
    }

    public static void main(String[] args) {
        // ใช้หน้าตาปุ่ม/ช่องกรอกของระบบปฏิบัติการ แทนสไตล์ Java รุ่นเก่า
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) { }
        SwingUtilities.invokeLater(() -> new PasswordApp().setVisible(true));
    }
}
