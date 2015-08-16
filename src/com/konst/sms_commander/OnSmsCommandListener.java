package com.konst.sms_commander;

import java.util.List;

/**
 * Created by Kostya on 20.05.2015.
 */
public interface OnSmsCommandListener {
    //void onEvent(String address, List<SmsCommander.Command> commandList);
    void onEvent(SmsCommander.Commands commands);
}
