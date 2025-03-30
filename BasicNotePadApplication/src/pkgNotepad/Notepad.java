package pkgNotepad;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.nio.channels.*;
import java.nio.file.*;

import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;

//For MYSql Connection
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Notepad {
    private static final String LOCK_FILE = "application.lock";  // Lock file to ensure single instance
    private static FileLock lock = null;  // To store the file lock
    private static FileChannel fileChannel = null;  // FileChannel to access the lock file

    //Global Declaration - or to Access across the same class file
    private static final String URL = "jdbc:mysql://localhost:3306/notepad"; // Change DB name if needed
    private static final String USER = "root"; // Change if using a different username
    private static final String PASSWORD = "root"; // Change if you have a password

    public static void main(String[] args) {
    	//Establishing the MySql Connection 
    	Connection dBConnection = getConnection();
        if (dBConnection != null) {
            System.out.println("Database connected successfully!");
            showNotification("Database connected successfully!");

        }
        else {
        	int choice = JOptionPane.showConfirmDialog(
                    null,
                    "Database connection failed! Do you want to continue?",
                    "Connection Failed",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
                );

                if (choice == JOptionPane.NO_OPTION) {
                    System.exit(0); // Exit if user chooses "No"
                }
        }
        // Try to ensure only one instance of the application is running
        if (isApplicationRunning()) {
            JOptionPane.showMessageDialog(null, "The application is already running!", "Instance Check", JOptionPane.WARNING_MESSAGE);
            return; // Exit the application if it's already running
        }
     // Add a shutdown hook to release the lock when the application is closed
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (lock != null) {
                    lock.release();  // Release the lock when the application shuts down
                }
                if (fileChannel != null) {
                    fileChannel.close();  // Close the file channel
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));
        System.out.println("Classpath: " + System.getProperty("java.class.path"));

        // Create the main frame
        JFrame mainWindow = new JFrame("Notepad Application");

        // Load the logo image from the resources directory
        URL iconUrl = Notepad.class.getResource("/resourcesFolder/NotepadLogo.png");
        System.out.println("Image is loaded or not  = "+iconUrl);
        if (iconUrl != null) {
            ImageIcon icon = new ImageIcon(iconUrl);  // Create an ImageIcon from the URL
            mainWindow.setIconImage(icon.getImage());  // Set the icon for the main window
        } else {
            System.err.println("Icon file not found!");
        }

        mainWindow.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);  // Don't immediately close when "X" is clicked
        mainWindow.setSize(500, 400);  // Increase window size to accommodate JTextArea
        mainWindow.setLocationRelativeTo(null);  // Center the window

        // Create a JTextArea for multi-line input
        JTextArea textArea = new JTextArea(10, 40);  // 10 rows, 40 columns
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);

        // Wrap JTextArea in a JScrollPane to make it scrollable
        JScrollPane scrollPane = new JScrollPane(textArea);
        mainWindow.getContentPane().add(scrollPane);  // Add the scrollable text area to the window

        // Add key event listener for Ctrl+S to save
        addKeyListenerToSave(mainWindow, textArea);

        // Add key event listener for Ctrl+Q to close the application
        addKeyListenerToClose(mainWindow);

        // Handle window close event (Clicking "X" button)
        mainWindow.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Prompt the user with a confirmation dialog before closing the app
                Object[] options = {"Yes", "No"}; // Custom button order
                int confirm = JOptionPane.showOptionDialog(
                    mainWindow,
                    "Are you sure you want to exit?",
                    "Exit",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[1] // Default selection (No)
                );
                if (confirm == JOptionPane.YES_OPTION) {
                    System.exit(0); // Close the application
                }
            }
        });

        // Make the main window visible
        mainWindow.setVisible(true);
    }



    static void addKeyListenerToClose(JFrame mainWindow) {
        // Add a key event listener for "Ctrl + Q" to close the application
        mainWindow.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0 && e.getKeyCode() == KeyEvent.VK_Q) {
                    // Ctrl + Q was pressed, confirm application close
                    int confirm = JOptionPane.showConfirmDialog(mainWindow, "Are you sure you want to exit?", "Exit", JOptionPane.YES_NO_OPTION);
                    if (confirm == JOptionPane.YES_OPTION) {
                        System.exit(0); // Close the application
                    }
                }
            }
        });

        // Make sure the JFrame can receive key events (focusable)
        mainWindow.setFocusable(true);
    }

    static void openSaveDialog(JFrame mainWindow, String content) {
        // Create a file chooser to let the user choose where to save the file
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save As");

        // Open a file dialog to let the user choose where to save the file
        int userSelection = fileChooser.showSaveDialog(mainWindow);

        // If the user clicks "Save"
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();

            // Check if the file has a valid extension (e.g., .txt). If not, append ".txt"
            if (!fileToSave.getName().endsWith(".txt")) {
                fileToSave = new File(fileToSave.getAbsolutePath() + ".txt");
            }

            // Save the content to the chosen file
            saveToFile(content, fileToSave);
        }
    }

    static void saveToFile(String content, File file) {
        if (content == null || content.trim().isEmpty()) {
            JOptionPane.showMessageDialog(null, "No data to save.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            // Create a FileWriter to write content to the chosen file
            FileWriter fileWriter = new FileWriter(file);

            // Get the current date and time
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String formattedTime = now.format(formatter);

            // Write the entered data to the file
            fileWriter.write(formattedTime + "::\n" + content + "\n");

            // Close the FileWriter to save changes
            fileWriter.close();

            JOptionPane.showMessageDialog(null, "Content saved successfully!", "Saved", JOptionPane.INFORMATION_MESSAGE);

        } catch (IOException e) {
            // If there is an error creating or writing to the file
            JOptionPane.showMessageDialog(null, "Error saving data to file.", "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }
    
    // Check if the application is already running by trying to bind to a port
    static boolean isApplicationRunning() {
        try {
            // Create the path to the lock file
            Path lockFilePath = Paths.get(LOCK_FILE);

            // Open the file channel to the lock file (create it if it doesn't exist)
            fileChannel = FileChannel.open(lockFilePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);

            // Try to acquire an exclusive lock on the file
            lock = fileChannel.tryLock();

            if (lock == null) {
                // If the lock couldn't be acquired, it means another instance is already running
                return true;
            }

            // Lock acquired successfully, proceed with the first instance
            return false;

        } catch (IOException e) {
            // If an exception occurs (e.g., file IO error), consider that the application might already be running
            e.printStackTrace();
            return true;  // Treat error as if the application is running
        }
    }


}
