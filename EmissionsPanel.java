package climate.client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.table.*;
import org.jfree.chart.*;
import org.jfree.chart.plot.*;
import org.jfree.data.general.*;

public class EmissionsPanel extends JPanel {
    private final ClimateControlGUI parent;
    private JTable deviceTable;
    private DeviceTableModel tableModel;
    private JButton logUsageBtn;
    private JButton getReportBtn;
    private JTextArea reportArea;
    private JFreeChart pieChart;
    
    public EmissionsPanel(ClimateControlGUI parent) {
        this.parent = parent;
        setLayout(new BorderLayout());
        
        tableModel = new DeviceTableModel();
        deviceTable = new JTable(tableModel);
        deviceTable.setFillsViewportHeight(true);
        
        JScrollPane tableScroll = new JScrollPane(deviceTable);
        add(tableScroll, BorderLayout.NORTH);
        
        JPanel buttonPanel = new JPanel();
        
        logUsageBtn = new JButton("Log Device Usage");
        logUsageBtn.addActionListener(e -> logDeviceUsage());
        buttonPanel.add(logUsageBtn);
        
        getReportBtn = new JButton("Get Emissions Report");
        getReportBtn.addActionListener(e -> getEmissionsReport());
        buttonPanel.add(getReportBtn);
        
        add(buttonPanel, BorderLayout.CENTER);
        
        
        JPanel bottomPanel = new JPanel(new GridLayout(1, 2));
        
        reportArea = new JTextArea(10, 30);
        reportArea.setEditable(false);
        bottomPanel.add(new JScrollPane(reportArea));
        
        createPieChart();
        bottomPanel.add(new ChartPanel(pieChart));
        
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    private void createPieChart() {
        DefaultPieDataset dataset = new DefaultPieDataset();
        dataset.setValue("No data", 1);
        
        pieChart = ChartFactory.createPieChart(
            "Carbon Emissions by Device",
            dataset,
            true,
            true,
            false
        );
        
        PiePlot plot = (PiePlot) pieChart.getPlot();
        plot.setSectionPaint("No data", Color.GRAY);
    }
    
    private void logDeviceUsage() {
        
    }
    
    private void getEmissionsReport() {
        
    }
    
    private static class DeviceTableModel extends AbstractTableModel {
        private final String[] columnNames = {"Device", "Hours Used", "Power (kW)"};
        private final Object[][] data = {
            {"Refrigerator", 24.0, 0.15},
            {"Air Conditioner", 8.0, 1.5},
            {"Washing Machine", 2.0, 0.5},
            {"TV", 4.0, 0.1},
            {"Computer", 6.0, 0.05}
        };
        
        @Override
        public int getRowCount() { return data.length; }
        
        @Override
        public int getColumnCount() { return columnNames.length; }
        
        @Override
        public Object getValueAt(int row, int col) { return data[row][col]; }
        
        @Override
        public String getColumnName(int col) { return columnNames[col]; }
        
        @Override
        public boolean isCellEditable(int row, int col) { return col != 0; }
        
        @Override
        public void setValueAt(Object value, int row, int col) {
            try {
                data[row][col] = Double.parseDouble(value.toString());
                fireTableCellUpdated(row, col);
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(null, "Please enter a valid number");
            }
        }
    }
    
    public void updateEmissionsReport(float totalCO2, String suggestions, Map<String, Double> emissionsByDevice) {
        SwingUtilities.invokeLater(() -> {
            reportArea.setText(String.format(
                "Total CO₂ Emissions: %.2f kg\n\nSuggestions:\n%s",
                totalCO2, suggestions));
            
            DefaultPieDataset dataset = new DefaultPieDataset();
            emissionsByDevice.forEach(dataset::setValue);
            
            pieChart.getPlot().setDataset(dataset);
        });
    }
}

