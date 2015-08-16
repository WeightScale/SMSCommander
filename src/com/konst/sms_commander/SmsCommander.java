/*
 * Copyright (c) 2015.
 */

package com.konst.sms_commander;

import android.telephony.SmsMessage;
import org.apache.http.message.BasicNameValuePair;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Класс обработки смс пакетов.
 *
 * @author Kostya
 */
public class SmsCommander {
    OnSmsCommandListener onSmsCommandListener;

    public SmsCommander(String codeword, Object[] objects, OnSmsCommandListener listener) throws Exception {
        setOnSmsCommandListener(listener);
        isCommand(codeword, objects);
    }

    public SmsCommander(String codeword, String address, String message, OnSmsCommandListener listener) throws Exception {
        setOnSmsCommandListener(listener);
        isCommand(codeword, address, message);
    }

    /**
     * Слушатель если есть смс пакет комманд.
     *
     * @param listener Слушатель.
     */
    public void setOnSmsCommandListener(OnSmsCommandListener listener) {
        onSmsCommandListener = listener;
    }

    /**
     * Проверяем является ли object пакетом команд.
     * Декодируем object, определяем если команда, запускаем процесс парсинга пакета комманд.
     *
     * @param codeword Ключ для декодирования сообщения.
     * @param objects  PDUs сообщения.
     * @throws Exception Это не команда.
     */
    private void isCommand(String codeword, Object[] objects) throws Exception {
        if (objects != null) {
            StringBuilder bodyText = new StringBuilder();
            String address = "";
            for (Object object : objects) {
                SmsMessage message = SmsMessage.createFromPdu((byte[]) object);
                address = message.getDisplayOriginatingAddress();
                //String date = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date(message.getTimestampMillis()));
                bodyText.append(message.getMessageBody());
            }

            String textSent = SMS.decrypt(codeword, bodyText.toString());
            String date = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date());
            new Thread(new ParsingSmsCommand(address, textSent, date)).start();
        }
    }

    /**
     * Проверяем является ли message пакетом команд.
     *
     * @param codeword Кодовое слово.
     * @param address  Адресс отправителя сообщения.
     * @param message  Текст сообщения.
     * @throws Exception Исключение при ошибки.
     */
    private void isCommand(String codeword, String address, String message) throws Exception {
        if (message != null) {

            String text = SMS.decrypt(codeword, message);
            String date = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date());
            new Thread(new ParsingSmsCommand(address, text, date)).start();
        }
    }



    /**
     * Парсер пакета комманд.
     * Формат сообщения [type(пакет)] type - тип пакета текстовый формат
     * Формат пакета [ [address] space [ [комманда 1] space [комманда 2] space [комманда n] ] ]
     * Формат комманды [ [имя комманды]=[параметр] ]
     * Формат параметра [ [[имя параметра 1]-[параметр 1]]_[[имя параметра 2]-[параметр 2]]_[[имя параметра n]-[параметр n]] ]
     */
    class ParsingSmsCommand implements Runnable {
        String mAddress;
        StringBuilder mPackage;
        final String date;
        private Commands mCommands;

        /**
         * Конструктор парсера.
         * В _package первоя команда это адресс отправителя, если адресс отправителя равен address тогда пакет парсится.
         * @param address  Адресс отправителя.
         * @param _package Пакет комманд.
         * @param date     Дата получения пакета.
         */
        ParsingSmsCommand(String address, String _package, String date) {
            mAddress = address;
            mPackage = new StringBuilder(_package);
            this.date = date;
        }

        @Override
        public void run() {
            if (mAddress == null)
                return;
            if (mPackage.indexOf("(")==-1)
                return;
            String typeCommand = mPackage.substring(0, mPackage.indexOf("("));
            if (typeCommand.isEmpty())
                return;
            mCommands = new Commands(typeCommand);
            mPackage.delete(0, mPackage.indexOf("(") + 1);
            mPackage = new StringBuilder(mPackage.subSequence(0, mPackage.indexOf(")")));

            try {
                if (mPackage.toString().isEmpty()) {
                    throw new Exception("message is empty");
                }
                mCommands.setAddress(mAddress);
                String[] sCommands = mPackage.toString().split(" ");
                for (String s : sCommands) {
                    String[] array = s.split("=");
                    if (array.length == 2) {
                        mCommands.addCommand(array[0], array[1]);
                    } else if (array.length == 1) {
                        mCommands.addCommand(array[0], "");
                    }
                }
                onSmsCommandListener.onEvent( mCommands);
            } catch (Exception e) { }

            /*if (mPackage.indexOf(" ") != -1) {
                String packageAddress = mPackage.substring(0, mPackage.indexOf(" "));
                if (!packageAddress.isEmpty()) {
                    if (packageAddress.length() > mAddress.length()) {
                        packageAddress = packageAddress.substring(packageAddress.length() - mAddress.length(), packageAddress.length());
                    } else if (packageAddress.length() < mAddress.length()) {
                        mAddress = mAddress.substring(mAddress.length() - packageAddress.length(), mAddress.length());
                    }
                    if (mAddress.equals(packageAddress)) {
                        mCommands.setAddress(mAddress);
                        mPackage.delete(0, mPackage.indexOf(" ") + 1);
                        try {
                            if (mPackage.toString().isEmpty()) {
                                throw new Exception("message is empty");
                            }
                            String[] sCommands = mPackage.toString().split(" ");
                            for (String s : sCommands) {
                                String[] array = s.split("=");
                                if (array.length == 2) {
                                    mCommands.addCommand(array[0], array[1]);
                                } else if (array.length == 1) {
                                    mCommands.addCommand(array[0], "");
                                }
                            }
                            onSmsCommandListener.onEvent( mCommands);
                        } catch (Exception e) { }
                    }
                }
            }*/


        }

    }

    public class Commands{
        String TAG;
        String address;
        public Map<String,String> map;

        Commands(String tag){
            TAG = tag;
            map = new  HashMap<>();
        }

        public String getTAG() { return TAG; }

        public void setTAG(String TAG) {  this.TAG = TAG;  }

        public Map<String, String> getMap() { return map; }

        public void setMap(Map<String, String> map) { this.map = map; }

        public void addCommand(String key, String value){ map.put(key, value); }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getAddress() {
            return address;
        }
    }
}
