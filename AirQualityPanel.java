package climate.client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import org.jfree.chart.*;
import org.jfree.chart.plot.*;
import org.jfree.data.time.*;
import org.jfree.data.xy.*;

public class AirQualityPanel extends JPanel {
    private final ClimateControlGUI parent;
    private JButton startMonitoringBtn;
    private JButton stopMonitoringBtn;
    private JComboBox<String> locationCombo;
    private JTextArea readingArea;
    private JFreeChart chart;
    private TimeSeries co2Series;
    private TimeSeries no2Series;
    private TimeSeries pm25Series;
    private boolean monitoring = false;
    
    public AirQualityPanel(ClimateControlGUI parent) {
        this.parent = parent;
        setLayout(new BorderLayout());
        
        
        JPanel controlPanel = new JPanel(new FlowLayout());
        
        locationCombo = new JComboBox<>(new String[]{"Living Room", "Bedroom", "Kitchen"});
        controlPanel.add(new JLabel("Location:"));
        controlPanel.add(locationCombo);
        
        startMonitoringBtn = new JButton("Start Monitoring");
        startMonitoringBtn.addActionListener(e -> startMonitoring());
        controlPanel.add(startMonitoringBtn);
        
        stopMonitoringBtn = new JButton("Stop Monitoring");
        stopMonitoringBtn.setEnabled(false);
        stopMonitoringBtn.addActionListener(e -> stopMonitoring());
        controlPanel.add(stopMonitoringBtn);
        
        add(controlPanel, BorderLayout.NORTH);
        
        
        createChart();
        ChartPanel chartPanel = new ChartPanel(chart);
        add(chartPanel, BorderLayout.CENTER);
        
        readingArea = new JTextArea(5, 40);
        readingArea.setEditable(false);
        add(new JScrollPane(readingArea), BorderLayout.SOUTH);
    }
    
    private void createChart() {
        co2Series = new TimeSeries("CO₂ (ppm)");
        no2Series = new TimeSeries("NO₂ (ppb)");
        pm25Series = new TimeSeries("PM2.5 (µg/m³)");
        
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(co2Series);
        dataset.addSeries(no2Series);
        dataset.addSeries(pm25Series);
        
        chart = ChartFactory.createTimeSeriesChart(
            "Air Quality Metrics",
            "Time",
            "Concentration",
            dataset,
            true,
            true,
            false
        );
        
        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
    }
    
    public void serviceConnected() {
        startMonitoringBtn.setEnabled(true);
    }
    
    private void startMonitoring() {
        if (monitoring) return;
        
        monitoring = true;
        startMonitoringBtn.setEnabled(false);
        stopMonitoringBtn.setEnabled(true);
        
        String location = (String) locationCombo.getSelectedItem();
        parent.log("Starting air quality monitoring in " + location);
        
    }
    
    private void stopMonitoring() {
        monitoring = false;
        startMonitoringBtn.setEnabled(true);
        stopMonitoringBtn.setEnabled(false);
        parent.log("Stopped air quality monitoring");
        
        
    }
    
    public void updateReading(float co2, float no2, float pm25) {
        SwingUtilities.invokeLater(() -> {
            // Update chart
            Millisecond now = new Millisecond();
            co2Series.addOrUpdate(now, co2);
            no2Series.addOrUpdate(now, no2);
            pm25Series.addOrUpdate(now, pm25);
            
            
            readingArea.append(String.format(
                "[%tT] CO₂: %.1f ppm, NO₂: %.1f ppb, PM2.5: %.1f µg/m³\n",
                new Date(), co2, no2, pm25));
        });
    }
}
