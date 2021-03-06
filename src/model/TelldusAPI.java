package model;

import DTO.ControlDevice;
import DTO.Device;
import DTO.Devices;
import interfaces.ChangeObserver;

import java.util.ArrayList;

public class TelldusAPI {
    private Devices deviceList;
    private static TelldusAPI instance = new TelldusAPI();
    private ArrayList<ChangeObserver> changeObservers = new ArrayList<>();

    private TelldusAPI() {
        // To prevent instantiation
    }

    public static TelldusAPI getInstance() {
        return instance;
    }

    public void restartTelldus() {
        ExecuteShellCommand shell = new ExecuteShellCommand();
        shell.executeCommand("sudo service telldusd restart");

    }

    /**
     * Adds new change observer that shall be notified when any changes on devices are performed
     * @param changeObserver ChangeObserver instance
     */
    public void addDeviceChangesObserver(ChangeObserver changeObserver){
        if(changeObserver != null)
            changeObservers.add(changeObserver);
    }

    public void removeDeviceChangesObserver(ChangeObserver changeObserver){
        if(changeObserver != null)
            changeObservers.remove(changeObserver);
    }

    /**
     * This method shall be called from all method in this class that perform changes on devices
     */
    private void notifyClientOnDeviceChange(){
        changeObservers.forEach(ChangeObserver::devicesChanged);
    }

    /**
     * Contacts OS and gets a new deviceList, updates class variable deviceList and returns list
     */
    public Devices updateDeviceList() {
        ExecuteShellCommand exe = new ExecuteShellCommand();
        String deviceListRaw = exe.executeCommand("tdtool --list-devices") + " "; //tailing space to find End of line
        //String deviceListRaw = "type=device     id=1    name=Example-device     lastsentcommand=ON\ntype=device     id=2    name=Uttag      lastsentcommand=OFF\n";

        ArrayList<Device> listOfDevices = new ArrayList<>();

        while(deviceListRaw.length() > 40) {
            //Get ID
            int idStart = deviceListRaw.indexOf("id");
            deviceListRaw = deviceListRaw.substring(idStart + 3);
            int idEnd = deviceListRaw.indexOf("\t");
            int id = Integer.parseInt(deviceListRaw.substring(0, idEnd));

            // Get name
            int nameStart = deviceListRaw.indexOf("name") + 5;
            deviceListRaw = deviceListRaw.substring(nameStart);
            int nameEnd = deviceListRaw.indexOf("\t");
            String name = deviceListRaw.substring(0, nameEnd);

            // Get lastsentcommand
            int lastSentStart = deviceListRaw.indexOf("lastsent") + 16;
            deviceListRaw= deviceListRaw.substring(lastSentStart);
            int lastSentEnd = deviceListRaw.indexOf("\n");
            String lastSent = deviceListRaw.substring(0, lastSentEnd);
            boolean status = false;
            if(lastSent.equals("ON"))
            {
                status = true;
            }
            int endOfLine = deviceListRaw.indexOf("\n");
            deviceListRaw = deviceListRaw.substring(endOfLine + 1);

            // Create device object and add to list of devices
            Device device = new Device(id, name, status);
            listOfDevices.add(device);
        }
        deviceList = new Devices(listOfDevices);
        return deviceList;
    }

    /**
     * Changes the status of device specified in the DTO
     */
    public void changeDeviceStatus(ControlDevice request) {
        updateDeviceList();
        int deviceId = request.getDeviceID();
        System.out.println("Switching device with id: " + deviceId);
        Device device = deviceList.getDeviceList().get(deviceId - 1);
        String newStatus;
        if (device.getStatus())
            newStatus = "off";
        else
            newStatus = "on";

        String command = "tdtool --" + newStatus + " " + deviceId;

        ExecuteShellCommand shell = new ExecuteShellCommand();
        shell.executeCommand(command);

        notifyClientOnDeviceChange();
    }

    public void learnDevice(Device device) {
        updateDeviceList();
        int newDeviceId = deviceList.getNumberOfDevices() + 1;
        device.setId(newDeviceId);

        String command = "\ndevice{\n   id=" + device.getId() +
                "\n   name=\"" + device.getName() + "\"\n   protocol=\"" + device.getProtocol() + "\"\n   model=\"" + device.getModel() +
                "\"\n   parameters{\n      house=\"A\"\n      unit=\"" + device.getId() + "\"\n   }\n}";

        ExecuteShellCommand shell = new ExecuteShellCommand();
        shell.appendToEndOfFile("/etc/tellstick.conf", command);
        System.out.println("Tellstick.conf updated with new device: " + device.getId());

        restartTelldus();

        command = "tdtool -e " + device.getId();
        shell.executeCommand(command);
        System.out.println("Tellstick has synchronized with device: " + device.getId());
        notifyClientOnDeviceChange();
    }
}

