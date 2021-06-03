package com.example.ecg_detection_app;

import java.util.UUID;

public class XenonHelper {

    private UUID mCharacteristicUUID;
    private byte[] mDeviceId;
    private byte[] mData;

    private enum MessageType {ADVERTISING, NOTIFICATION};

    private MessageType mMessageType;

    public final static UUID UUID_XENON_SERVICE = UUID
            .fromString("0000fff0-0000-1000-8000-00805f9b34fb");
    public final static UUID UUID_XENON_DATA = UUID
            .fromString("0000fff5-0000-1000-8000-00805f9b34fb");
    public final static UUID UUID_CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    //public final static String XENON_ADVERTISING_NAME = "xb42";
    public final static String XENON_ADVERTISING_NAME = "xb40";

    public XenonHelper(byte[] deviceId, UUID characteristicUUID, byte[] data) {
        super();
        this.mDeviceId = deviceId;
        this.mCharacteristicUUID = characteristicUUID;
        this.mData = data;
        this.mMessageType = MessageType.NOTIFICATION;
    }

    public XenonHelper(byte[] data) {
        super();
        this.mData = data;
        this.mMessageType = MessageType.ADVERTISING;
    }
}
