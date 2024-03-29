package com.n3v.junwidi.Adapter;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.n3v.junwidi.R;

import java.util.ArrayList;
import java.util.Collection;

/*
Client Activity 의 ListView 가 사용할 ArrayAdapter.
WifiP2pDeviceList 의 정보를 ListView 에 띄워줌.

view.setText() 의 괄호 안의 내용은 String 만 받으므로 다른 type 의 데이터는 형변환을 해줘야함
 */
public class MyClientAdapter extends ArrayAdapter<WifiP2pDevice> {

    private static final String TAG = "MyClientAdapter";

    private ArrayList<WifiP2pDevice> myDeviceArrayList;
    private Context myContext;
    private int myResource;

    public MyClientAdapter(Context context, int resource, ArrayList<WifiP2pDevice> deviceArrayList) {
        super(context, resource, deviceArrayList);
        myContext = context;
        myResource = resource;
        myDeviceArrayList = deviceArrayList;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            LayoutInflater li = (LayoutInflater) myContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = li.inflate(this.myResource, null);
        }
        WifiP2pDevice tempDevice = myDeviceArrayList.get(position);
        if (tempDevice != null) {
            TextView item_Server_Device_Name = (TextView) v.findViewById(R.id.item_server_device_name);
            TextView item_Server_Device_Address = (TextView) v.findViewById(R.id.item_server_device_address);
            if (tempDevice.deviceName != null) {
                item_Server_Device_Name.setText(tempDevice.deviceName);
            }
            if (tempDevice.deviceAddress != null) {
                item_Server_Device_Address.setText(tempDevice.deviceAddress);
            }
        }
        return v;
    }

    public void add(WifiP2pDevice device) {
        myDeviceArrayList.add(device);
    }

    public void addAll(Collection c) {
        myDeviceArrayList.clear();
        myDeviceArrayList.addAll(c);
    }

    @Override
    public int getCount() {
        if (myDeviceArrayList == null) {
            return -1;
        }
        return myDeviceArrayList.size();
    }

    @Override
    public WifiP2pDevice getItem(int position) {
        return myDeviceArrayList.get(position);
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }
}
