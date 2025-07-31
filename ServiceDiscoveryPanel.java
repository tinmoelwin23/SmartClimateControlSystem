package climate.client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import javax.jmdns.ServiceInfo;

public class ServiceDiscoveryPanel extends JPanel {
    private final ClimateControlGUI parent;
    private DefaultListModel<ServiceInfo> serviceListModel;
    
    public ServiceDiscoveryPanel(ClimateControlGUI parent) {
        this.parent = parent;
        setLayout(new BorderLayout());
        
        serviceListModel = new DefaultListModel<>();
        JList<ServiceInfo> serviceList = new JList<>(serviceListModel);
        serviceList.setCellRenderer(new ServiceListRenderer());
        
        JScrollPane scrollPane = new JScrollPane(serviceList);
        add(scrollPane, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout());
        
        JButton discoverBtn = new JButton("Discover Services");
        discoverBtn.addActionListener(e -> discoverServices());
        
        JButton connectBtn = new JButton("Connect Selected");
        connectBtn.addActionListener(e -> connectToSelected(serviceList.getSelectedValue()));
        
        buttonPanel.add(discoverBtn);
        buttonPanel.add(connectBtn);
        add(buttonPanel, BorderLayout.SOUTH);
    }
    
    private void discoverServices() {
        parent.log("Starting service discovery...");
    }
    
    private void connectToSelected(ServiceInfo info) {
        if (info == null) {
            JOptionPane.showMessageDialog(this, "Please select a service first");
            return;
        }
        
        if (info.getType().contains("airquality")) {
            parent.connectToAirQualityService(info);
        } else if (info.getType().contains("thermostat")) {
            parent.connectToThermostatService(info);
        } else if (info.getType().contains("emissions")) {
            parent.connectToEmissionsService(info);
        }
    }
    
    private static class ServiceListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
            JList<?> list, Object value, int index,
            boolean isSelected, boolean cellHasFocus) {
            
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            if (value instanceof ServiceInfo) {
                ServiceInfo info = (ServiceInfo) value;
                setText(info.getName() + " (" + info.getType() + ")");
                setToolTipText("Address: " + info.getHostAddresses()[0] + 
                    ":" + info.getPort());
            }
            
            return this;
        }
    }
}

