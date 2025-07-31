package climate.client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.jmdns.ServiceInfo;
import io.grpc.*;

public class ClimateControlGUI extends JFrame {
    
    private AirQualityMonitorGrpc.AirQualityMonitorStub airQualityStub;
    private SmartThermostatGrpc.SmartThermostatBlockingStub thermostatStub;
    private CarbonEmissionTrackerGrpc.CarbonEmissionTrackerStub emissionsStub;
    
    private JTabbedPane tabbedPane;
    private ServiceDiscoveryPanel discoveryPanel;
    private AirQualityPanel airQualityPanel;
    private ThermostatControlPanel thermostatPanel;
    private EmissionsPanel emissionsPanel;
    private JTextArea logArea;
    
    public ClimateControlGUI() {
        super("Smart Climate Control System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLayout(new BorderLayout());
        
        initializeComponents();
        setupMenu();
        
        setVisible(true);
    }
    
    private void initializeComponents() {
        tabbedPane = new JTabbedPane();
        
        
        discoveryPanel = new ServiceDiscoveryPanel(this);
        tabbedPane.addTab("Service Discovery", discoveryPanel);
        
        airQualityPanel = new AirQualityPanel(this);
        tabbedPane.addTab("Air Quality", airQualityPanel);
        
        thermostatPanel = new ThermostatControlPanel(this);
        tabbedPane.addTab("Thermostat", thermostatPanel);
        
        emissionsPanel = new EmissionsPanel(this);
        tabbedPane.addTab("Emissions", emissionsPanel);
        
        add(tabbedPane, BorderLayout.CENTER);
        
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setPreferredSize(new Dimension(0, 150));
        add(logScroll, BorderLayout.SOUTH);
    }
    
    private void setupMenu() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);
        
        JMenu helpMenu = new JMenu("Help");
        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> showAboutDialog());
        helpMenu.add(aboutItem);
        menuBar.add(helpMenu);
        
        setJMenuBar(menuBar);
    }
    
    private void showAboutDialog() {
        JOptionPane.showMessageDialog(this,
            "Smart Climate Control System\nVersion 1.0\n\n" +
            "A distributed system for monitoring and controlling\n" +
            "indoor climate while tracking carbon emissions.",
            "About",
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    public void connectToAirQualityService(ServiceInfo info) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(
                info.getHostAddresses()[0], info.getPort())
            .usePlaintext()
            .build();
        
        airQualityStub = AirQualityMonitorGrpc.newStub(channel);
        airQualityPanel.serviceConnected();
        log("Connected to Air Quality service at " + info.getHostAddresses()[0] + ":" + info.getPort());
    }
    
    public void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + new Date() + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClimateControlGUI());
    }
}

