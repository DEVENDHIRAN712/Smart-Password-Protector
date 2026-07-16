import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class PasswordManagerGUI {

    private static final String SECRET_KEY = "MySecretKey12345";
    private static final String FILE_NAME = "passwords.txt";
    private static Map<String, String> passwordStore = new HashMap<>();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PasswordManagerGUI().createAndShowGUI());
    }

    private void createAndShowGUI() {
        loadPasswords();

        JFrame frame = new JFrame("🔐 Smart Password Protector (Face + Master)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 400);
        frame.setLayout(new BorderLayout());

        JLabel label = new JLabel("Welcome to Smart Password Protector", SwingConstants.CENTER);
        label.setFont(new Font("Segoe UI", Font.BOLD, 18));
        frame.add(label, BorderLayout.NORTH);

        JButton unlockButton = new JButton("🔓 Unlock using Face or Master Key");
        unlockButton.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        frame.add(unlockButton, BorderLayout.CENTER);

        unlockButton.addActionListener(e -> authenticateUser(frame));

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void authenticateUser(JFrame frame) {
        File f = new File("registered_face.png");
        if (!f.exists()) {
            JOptionPane.showMessageDialog(frame, "No registered face found. Please register your face first!");
            FaceAuth.reRegisterFace();
            return;
        }

        boolean faceAuth = FaceAuth.authenticateFace();

        if (faceAuth) {
            JOptionPane.showMessageDialog(frame, "✅ Face Verified Successfully!");
            openMainMenu(frame);
        } else {
            JOptionPane.showMessageDialog(frame, "❌ Face not recognized!");
            String masterKey = JOptionPane.showInputDialog(frame, "Enter Master Password:");
            if (masterKey != null && masterKey.equals("admin123")) {
                JOptionPane.showMessageDialog(frame, "✅ Access granted via Master Password!");
                openMainMenu(frame);
            } else {
                JOptionPane.showMessageDialog(frame, "❌ Access denied!");
            }
        }
    }

    private void openMainMenu(JFrame frame) {
        JFrame menu = new JFrame("Password Manager Menu");
        menu.setSize(600, 500);
        menu.setLayout(new GridLayout(6, 1));

        JButton addBtn = new JButton("➕ Add Password");
        JButton viewBtn = new JButton("📂 View Passwords");
        JButton editBtn = new JButton("📝 Edit Password");
        JButton deleteBtn = new JButton("🗑 Delete Password");
        JButton reFaceBtn = new JButton("♻ Re-register Face");
        JButton exitBtn = new JButton("🚪 Exit");

        addBtn.addActionListener(e -> addPassword());
        viewBtn.addActionListener(e -> viewPasswords());
        editBtn.addActionListener(e -> editPassword());
        deleteBtn.addActionListener(e -> deletePassword());

        reFaceBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(null,
                    "Are you sure you want to re-register your face?",
                    "Confirm", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                boolean success = FaceAuth.reRegisterFace();
                JOptionPane.showMessageDialog(null,
                        success ? "✅ Face re-registered successfully!"
                                : "❌ Re-registration failed!");
            }
        });

        exitBtn.addActionListener(e -> {
            savePasswords();
            menu.dispose();
            frame.dispose();
            System.exit(0);
        });

        menu.add(addBtn);
        menu.add(viewBtn);
        menu.add(editBtn);
        menu.add(deleteBtn);
        menu.add(reFaceBtn);
        menu.add(exitBtn);

        menu.setLocationRelativeTo(null);
        menu.setVisible(true);
    }

    // ✅ Add new password
    private void addPassword() {
        String site = JOptionPane.showInputDialog("Enter Website/App Name:");
        if (site == null || site.isEmpty()) return;

        String password = JOptionPane.showInputDialog("Enter Password:");
        if (password == null || password.isEmpty()) return;

        try {
            String encrypted = encrypt(password, SECRET_KEY);
            passwordStore.put(site, encrypted);
            savePasswords();
            JOptionPane.showMessageDialog(null, "✅ Password saved securely!");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "❌ Encryption failed!");
        }
    }

    // ✅ Edit existing password
    private void editPassword() {
        if (passwordStore.isEmpty()) {
            JOptionPane.showMessageDialog(null, "No passwords to edit!");
            return;
        }

        String site = JOptionPane.showInputDialog("Enter the Website/App name to edit:");
        if (site == null || site.isEmpty()) return;

        if (!passwordStore.containsKey(site)) {
            JOptionPane.showMessageDialog(null, "❌ No entry found for: " + site);
            return;
        }

        String newPassword = JOptionPane.showInputDialog("Enter new password for " + site + ":");
        if (newPassword == null || newPassword.isEmpty()) return;

        try {
            String encrypted = encrypt(newPassword, SECRET_KEY);
            passwordStore.put(site, encrypted);
            savePasswords();
            JOptionPane.showMessageDialog(null, "✅ Password updated successfully!");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "❌ Update failed!");
        }
    }

    // ✅ Delete password
    private void deletePassword() {
        if (passwordStore.isEmpty()) {
            JOptionPane.showMessageDialog(null, "No passwords to delete!");
            return;
        }

        String site = JOptionPane.showInputDialog("Enter the Website/App name to delete:");
        if (site == null || site.isEmpty()) return;

        if (passwordStore.containsKey(site)) {
            int confirm = JOptionPane.showConfirmDialog(null,
                    "Are you sure you want to delete password for: " + site + "?",
                    "Confirm Delete", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                passwordStore.remove(site);
                savePasswords();
                JOptionPane.showMessageDialog(null, "🗑 Password deleted successfully!");
            }
        } else {
            JOptionPane.showMessageDialog(null, "❌ No entry found for: " + site);
        }
    }

    // ✅ View all passwords
    private void viewPasswords() {
        if (passwordStore.isEmpty()) {
            JOptionPane.showMessageDialog(null, "No passwords found!");
            return;
        }

        StringBuilder sb = new StringBuilder("Stored Passwords:\n\n");
        for (Map.Entry<String, String> entry : passwordStore.entrySet()) {
            try {
                String decrypted = decrypt(entry.getValue(), SECRET_KEY);
                sb.append(entry.getKey()).append(" → ").append(decrypted).append("\n");
            } catch (Exception e) {
                sb.append(entry.getKey()).append(" → [Error Decrypting]\n");
            }
        }

        JTextArea textArea = new JTextArea(sb.toString());
        textArea.setEditable(false);
        JScrollPane scroll = new JScrollPane(textArea);
        JOptionPane.showMessageDialog(null, scroll);
    }

    // ✅ Encryption
    private String encrypt(String strToEncrypt, String secret) throws Exception {
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return Base64.getEncoder().encodeToString(cipher.doFinal(strToEncrypt.getBytes()));
    }

    // ✅ Decryption
    private String decrypt(String strToDecrypt, String secret) throws Exception {
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, key);
        return new String(cipher.doFinal(Base64.getDecoder().decode(strToDecrypt)));
    }

    // ✅ Save passwords to file
    private void savePasswords() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(FILE_NAME))) {
            for (Map.Entry<String, String> entry : passwordStore.entrySet())
                writer.println(entry.getKey() + ":" + entry.getValue());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Error saving passwords!");
        }
    }

    // ✅ Load passwords from file
    private void loadPasswords() {
        File file = new File(FILE_NAME);
        if (!file.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(FILE_NAME))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2)
                    passwordStore.put(parts[0], parts[1]);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Error loading passwords!");
        }
    }
}

