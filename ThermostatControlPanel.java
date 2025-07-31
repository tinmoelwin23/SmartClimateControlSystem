package climate.client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.event.*;

public class ThermostatControlPanel extends JPanel {
    private final ClimateControlGUI parent;
    private JComboBox<String> roomCombo;
    private JSlider tempSlider;
    private JLabel currentTempLabel;
    private JLabel powerUsageLabel;
    private JButton setTempBtn;
    private JCheckBox occupancyCheck;
    private JTextArea statusArea;
    
    public ThermostatControlPanel(ClimateControlGUI parent) {
        this.parent = parent;
        setLayout(new BorderLayout());
        
        
        JPanel controlPanel = new JPanel(new GridLayout(0, 2, 10, 10));
        
        roomCombo = new JComboBox<>(new String[]{"Living Room", "Bedroom", "Kitchen"});
        controlPanel.add(new JLabel("Room:"));
        controlPanel.add(roomCombo);
        
        tempSlider = new JSlider(JSlider.HORIZONTAL, 15, 30, 22);
        tempSlider.setMajorTickSpacing(5);
        tempSlider.setMinorTickSpacing(1);
        tempSlider.setPaintTicks(true);
        tempSlider.setPaintLabels(true);
        tempSlider.addChangeListener(e -> updateTempPreview());
        controlPanel.add(new JLabel("Temperature:"));
        controlPanel.add(tempSlider);
        
        currentTempLabel = new JLabel("Current: --°C");
        controlPanel.add(new JLabel("Current Temperature:"));
        controlPanel.add(currentTempLabel);
        
        powerUsageLabel = new JLabel("Power usage: --");
        controlPanel.add(new JLabel("Estimated Power:"));
        controlPanel.add(powerUsageLabel);
        
        occupancyCheck = new JCheckBox("Room Occupied");
        occupancyCheck.addActionListener(e -> sendOccupancyUpdate());
        controlPanel.add(new JLabel("Occupancy:"));
        controlPanel.add(occupancyCheck);
        
        setTempBtn = new JButton("Set Temperature");
        setTempBtn.addActionListener(e -> setTemperature());
        controlPanel.add(new JLabel(""));
        controlPanel.add(setTempBtn);
        
        add(controlPanel, BorderLayout.NORTH);
        
        statusArea = new JTextArea(10, 40);
        statusArea.setEditable(false);
        add(new JScrollPane(statusArea), BorderLayout.CENTER);
    }
    
    private void updateTempPreview() {
        currentTempLabel.setText("Current: " + tempSlider.getValue() + "°C");
    }
    
    private void setTemperature() {
        String room = (String) roomCombo.getSelectedItem();
        int temp = tempSlider.getValue();
        
        parent.log("Setting temperature in " + room + " to " + temp + "°C");
        
    }
    
    private void sendOccupancyUpdate() {
        String room = (String) roomCombo.getSelectedItem();
        boolean occupied = occupancyCheck.isSelected();
        
        parent.log("Sending occupancy update for " + room + ": " + 
            (occupied ? "occupied" : "unoccupied"));
        
        
    }
    
    public void updateHVACState(String mode, float temp, String efficiency, String powerUsage) {
        SwingUtilities.invokeLater(() -> {
            statusArea.append(String.format(
                "[%tT] HVAC Mode: %s, Temperature: %.1f°C, Efficiency: %s, Power: %s\n",
                new Date(), mode, temp, efficiency, powerUsage));
            
            powerUsageLabel.setText("Power usage: " + powerUsage);
        });
    }
}
