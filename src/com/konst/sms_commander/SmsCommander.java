/*
 * Copyright (c) 2015.
 */

package com.konst.sms_commander;

import android.telephony.SmsMessage;
import org.apache.http.message.BasicNameValuePair;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 *  Класс обработки смс пакетов.
 *  @author Kostya
 */
public class SmsCommander {
    OnSmsCommandListener onSmsCommandListener;

    public SmsCommander(String codeword, Object[] objects, OnSmsCommandListener listener) throws Exception{
        setOnSmsCommandListener(listener);
        isCommand(codeword, objects);
    }

    public SmsCommander(String codeword, String message, OnSmsCommandListener listener) throws Exception{
        setOnSmsCommandListener(listener);
        isCommand(codeword, message);
    }

    /** Слушатель если есть смс пакет комманд.
     *  @param listener Слушатель.
     */
    public void setOnSmsCommandListener(OnSmsCommandListener listener) {
        onSmsCommandListener = listener;
    }

    /** Проверяем является ли object пакетом команд.
     *  Декодируем object, определяем если команда, запускаем процесс парсинга пакета комманд.
     *  @param codeword Ключ для декодирования сообщения.
     *  @param objects PDUs сообщения.
     *  @exception Exception Это не команда.
     */
    private void isCommand(String codeword, Object[] objects) throws Exception{
        if (objects != null){
            StringBuilder bodyText = new StringBuilder();
            String address = "";
            for (Object object : objects) {
                SmsMessage message = SmsMessage.createFromPdu((byte[]) object);
                address = message.getDisplayOriginatingAddress();
                bodyText.append(message.getMessageBody());
            }

            String textSent = SMS.decrypt(codeword, bodyText.toString());
            String date = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date());
            new Thread(new ParsingSmsCommand("+380503285426", textSent, date)).start(); //todo поменять телефон на address
        }
    }

    private void isCommand(String codeword, String message) throws Exception{
        if (message != null){

            String textSent = SMS.decrypt(codeword, message);
            String date = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date());
            new Thread(new ParsingSmsCommand("+380503285426", textSent, date)).start(); //todo поменять телефон на address
        }
    }

    /** Парсер пакета комманд.
     *  Формат пакета [ [address] space [ [комманда 1] space [комманда 2] space [комманда n] ] ]
     *  Формат комманды [ [имя комманды]=[параметр] ]
     *  Формат параметра [ [[значение 1]-[параметр 2]]_[[значение 2]-[параметр 2]]_[[значение n]-[параметр n]] ]
     */
    class ParsingSmsCommand implements Runnable {
        String mAddress;
        final StringBuilder mPackage;
        final String date;

        /** Конструктор парсера.
         *  @param address Адресс отправителя.
         *  @param _package Пакет комманд.
         *  @param date Дата получения пакета. */
        ParsingSmsCommand(String address, String _package, String date) {
            mAddress = address;
            mPackage = new StringBuilder(_package);
            this.date = date;
        }

        @Override
        public void run() {
            if (mAddress == null)
                return;
            if (mPackage.indexOf(" ") != -1) {
                String packageAddress = mPackage.substring(0, mPackage.indexOf(" "));
                if (!packageAddress.isEmpty()) {
                    if (packageAddress.length() > mAddress.length()) {
                        packageAddress = packageAddress.substring(packageAddress.length() - mAddress.length(), packageAddress.length());
                    } else if (packageAddress.length() < mAddress.length()) {
                        mAddress = mAddress.substring(mAddress.length() - packageAddress.length(), mAddress.length());
                    }
                    if (mAddress.equals(packageAddress)) {
                        mPackage.delete(0, mPackage.indexOf(" ") + 1);
                        //StringBuilder textSent = new StringBuilder();
                        try {
                            if (mPackage.toString().isEmpty()) {
                                throw new Exception("message is empty");
                            }
                            String[] commands = mPackage.toString().split(" ");
                            List<Command> results = new ArrayList<>();
                            for (String s : commands) {
                                String[] array = s.split("=");
                                if (array.length == 2) {
                                    results.add(new Command(array[0], array[1]));
                                } else if (array.length == 1) {
                                    results.add(new Command(array[0], ""));
                                }
                            }
                            onSmsCommandListener.onEvent(mAddress, results);
                        } catch (Exception e) {
                            //textSent.append(e.getMessage());
                        }

                        try {
                            //SMS.sendSMS(address, textSent.toString());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

    }

    public class Command{
        String name;
        String value;

        Command(String name, String value){
            this.name = name;
            this.value = value;
        }

        public String getName() {  return name;}

        public void setName(String name) { this.name = name;  }

        public String getValue() { return value;  }

        public void setValue(String value) {  this.value = value;  }
    }
}
