package com.n3v.junwidi;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.exoplayer2.ExoPlayer;
import com.n3v.junwidi.Adapter.MyServerAdapter;
import com.n3v.junwidi.BroadcastReceiver.MyBroadCastReceiver;
import com.n3v.junwidi.Datas.DeviceInfo;
import com.n3v.junwidi.Dialogs.GuideLineDialog;
import com.n3v.junwidi.Dialogs.LoadingSpinnerDialog;
import com.n3v.junwidi.Dialogs.SendDialog;
import com.n3v.junwidi.Listener.MyDialogListener;
import com.n3v.junwidi.Listener.MyDirectActionListener;
import com.n3v.junwidi.Listener.MyGuidelineDialogListener;
import com.n3v.junwidi.Listener.MyServerTaskListener;
import com.n3v.junwidi.Services.MyServerTask;
import com.n3v.junwidi.Utils.DeviceInfoListUtil;
import com.n3v.junwidi.Utils.RealPathUtil;

import java.util.ArrayList;
import java.util.StringTokenizer;

import static java.lang.Thread.sleep;

public class ServerActivity extends BaseActivity implements MyDirectActionListener, MyDialogListener, MyServerTaskListener, MyGuidelineDialogListener {

    private static final String TAG = "ServerActivity";

    private WifiP2pManager myManager;
    private WifiP2pManager.Channel myChannel;
    private boolean isWifiP2pEnabled = false;
    private boolean isGroupExist = false;

    private MyBroadCastReceiver myBroadCastReceiver;

    private TextView txt_myDevice_Name;
    private TextView txt_myDevice_Address;
    //private TextView txt_myDevice_State;
    private TextView txt_Video_Path;
    private Button btn_File_Select;
    private Button btn_File_Transfer;
    private Button btn_Exo;
    private SwipeRefreshLayout layout_Server_Pull_To_Refresh;
    private ListView listView_Client_List;

    private ArrayList<WifiP2pDevice> myWifiP2pDeviceList = new ArrayList<>();
    private MyServerAdapter myServerAdapter;

    private WifiP2pInfo myWifiP2pInfo = null;
    private WifiP2pDevice myWifiP2pDevice = null;
    private DeviceInfo myDeviceInfo = null;
    private ArrayList<DeviceInfo> myDeviceInfoList = new ArrayList<>();
    private ArrayList<DeviceInfo> processedDIL = new ArrayList<>();

    private String videoPath = "";
    private boolean isFileSelected = false;
    private static final int PICK_VIDEO_RESULT_CODE = 1111;
    private static final int VIDEO_PLAYER_RESULT_CODE = 2222;

    SendDialog sendDialog = null;
    LoadingSpinnerDialog spinningDialog = null;
    GuideLineDialog guideLineDialog = null;

    private boolean log_on = true;
    private boolean first_wifi_check = false;

    AsyncTask nowTask = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);

        myManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        myChannel = myManager.initialize(this, getMainLooper(), null);
        myBroadCastReceiver = new MyBroadCastReceiver(myManager, myChannel, this);
        sendDialog = new SendDialog(this, "", this);
        spinningDialog = new LoadingSpinnerDialog(this);
        DisplayMetrics dm = getApplicationContext().getResources().getDisplayMetrics();
        guideLineDialog = new GuideLineDialog(this, dm, GuideLineDialog.GLD_HOST, this);

        permissionCheck(0);

        if (!first_wifi_check) {
            int checkWifiState = checkWifiOnAndConnected();
            if (checkWifiState == 2) {
                WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
                if (wifiManager != null) {
                    showToast("Wifi 기능을 사용합니다");
                    wifiManager.setWifiEnabled(true);
                }
            } else if (checkWifiState == 3) {
                WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
                if (wifiManager != null) {
                    //showToast("Wifi 연결을 해제하였습니다");
                    wifiManager.disconnect();
                }
            }
            first_wifi_check = true;
        }

        if (!isLocationServiceEnabled()) {
            showToast("위치정보 기능이 필요합니다");
            Intent locationIntent = new Intent( Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(locationIntent);
        }


        initView();

    }

    private void initView() {
        txt_myDevice_Name = findViewById(R.id.server_txt_my_device_name);
        //txt_Video_Path = findViewById(R.id.server_txt_video_path);
        //txt_myDevice_Address = findViewById(R.id.server_txt_my_device_address);
        txt_Video_Path = findViewById(R.id.server_txt_video_path);
        txt_myDevice_Address = findViewById(R.id.server_txt_my_device_address);
        //txt_myDevice_State = findViewById(R.id.server_txt_my_device_state);
        txt_Video_Path = findViewById(R.id.server_txt_video_path);
        btn_File_Select = findViewById(R.id.server_btn_file_select);
        btn_File_Select.setEnabled(true);
        btn_File_Select.setAlpha(1f);
        btn_File_Transfer = findViewById(R.id.server_btn_file_transfer);
        btn_File_Transfer.setEnabled(false);
        btn_File_Transfer.setAlpha(0.3f);
        btn_Exo = findViewById(R.id.exo_button);
        btn_Exo.setEnabled(false);
        btn_Exo.setAlpha(0.3f);

        btn_File_Select.setOnClickListener(myClickListener);
        btn_File_Transfer.setOnClickListener(myClickListener);

        listView_Client_List = findViewById(R.id.server_list_client);
        myServerAdapter = new MyServerAdapter(this, R.layout.item_client, myDeviceInfoList);
        listView_Client_List.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
            }
        });
        listView_Client_List.setAdapter(myServerAdapter);
        layout_Server_Pull_To_Refresh = findViewById(R.id.server_layout_pull_to_refresh);
        layout_Server_Pull_To_Refresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (isWifiP2pEnabled) {
                    myManager.requestGroupInfo(myChannel, new WifiP2pManager.GroupInfoListener() {
                        @Override
                        public void onGroupInfoAvailable(WifiP2pGroup wifiP2pGroup) {
                            deviceListUpdate(wifiP2pGroup);
                        }
                    });
                    myServerAdapter.notifyDataSetChanged();
                }
                layout_Server_Pull_To_Refresh.setRefreshing(false);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        getSupportActionBar().setTitle("연결 상태");
        return true;
    }

    private View.OnClickListener myClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.equals(btn_File_Select)) {
                Log.v(TAG, "btn_File_Select onClick");
                if (!isFileSelected) {
                    permissionCheck(0);
                    Intent fileChooseIntent = new Intent(Intent.ACTION_GET_CONTENT);
                    fileChooseIntent.setType("video/*");
                    startActivityForResult(fileChooseIntent, PICK_VIDEO_RESULT_CODE);
                } else {
                    videoPath = "";
                    txt_Video_Path.setText("버튼을 눌러 비디오를 선택해 주세요");
                    isFileSelected = false;
                    btn_File_Transfer.setEnabled(false);
                    btn_File_Transfer.setAlpha(0.3f);
                    for (DeviceInfo di : myDeviceInfoList) {
                        di.setHasVideo(false);
                        di.setVideoName("");
                    }
                }
                //callServerTask(MyServerTask.SERVER_MESSAGE_SERVICE);
            } else if (v.equals(btn_File_Transfer)) {
                if (isFileSelected) {
                    Log.v(TAG, "btn_File_Transfer onClick");
                    permissionCheck(0);
                    StringTokenizer st = new StringTokenizer(videoPath, "/");
                    String videoName = "";
                    while (st.hasMoreTokens()) {
                        videoName = st.nextToken();
                    }
                    sendDialog.show();
                    sendDialog.initDialog();
                    sendDialog.setFileName(videoName);
                    sendDialog.setReceivers(myDeviceInfoList.size());
                    btn_Exo.setEnabled(true);
                    btn_Exo.setAlpha(1f);
                }
            }
        }
    };

    public void enterExoplay(View view) {
        if (isFileSelected) {
            Log.v(TAG, "processedDIL.size = " + processedDIL.size());
            btn_Exo.setEnabled(true);
            btn_Exo.setAlpha(1f);
            if (processedDIL.size() == 0) {
                Intent intent = new Intent(this, Exoplay.class);
                intent.putExtra("videoPath", videoPath);
                startActivity(intent);
                overridePendingTransition(R.anim.anim_slide_in_right, R.anim.anim_slide_out_left);
            } else {
                guideLineDialog.show();
                guideLineDialog.setProcessedMyDeviceInfo(myDeviceInfo);
                callServerTask(MyServerTask.SERVER_TCP_SHOW_GUIDELINE_SERVICE);
            }
        }
    } //xml 파일을 통해 onClick으로 호출됨


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case PICK_VIDEO_RESULT_CODE:
                if (resultCode == RESULT_OK) {
                    //videoPath = data.getData().getPath();
                    Uri videoURI = data.getData();
                    videoPath = RealPathUtil.getRealPath(this, videoURI);
                    Log.v(TAG, videoPath + " selected");
                    txt_Video_Path.setText(videoPath);
                    isFileSelected = true;

                    btn_File_Transfer.setEnabled(true);
                    btn_File_Transfer.setAlpha(1f);
                    btn_Exo.setEnabled(true);
                    btn_Exo.setAlpha(1f);
                    showToast(videoPath + " selected");
                }
            case VIDEO_PLAYER_RESULT_CODE:
                if (resultCode == RESULT_OK) {
                }
        }
    }

    public AsyncTask callServerTask(String mode) {
        if (myWifiP2pInfo != null && (mode.equals(MyServerTask.SERVER_TCP_FILE_TRANSFER_SERVICE) || mode.equals(MyServerTask.SERVER_FILE_TRANSFER_SERVICE))) {
            Log.v(TAG, "callServerTask : mode.FILE_TRANSFER");
            if (!this.videoPath.equals("")) {
                return new MyServerTask(this, mode, myWifiP2pInfo.groupOwnerAddress.getHostAddress(), myDeviceInfo, myDeviceInfoList, myServerAdapter, this.videoPath, this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        } else if (myWifiP2pInfo != null && mode.equals(MyServerTask.SERVER_TCP_SHOW_GUIDELINE_SERVICE)) {
            return new MyServerTask(this, mode, myWifiP2pInfo.groupOwnerAddress.getHostAddress(), myDeviceInfo, processedDIL, myServerAdapter, this.videoPath, this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else if (myWifiP2pInfo != null) {
            return new MyServerTask(this, mode, myWifiP2pInfo.groupOwnerAddress.getHostAddress(), myDeviceInfo, myDeviceInfoList, myServerAdapter, this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
        return null;
    }

    @Override
    public void onResume() {
        super.onResume();
        myBroadCastReceiver = new MyBroadCastReceiver(myManager, myChannel, this);
        registerReceiver(myBroadCastReceiver, MyBroadCastReceiver.getIntentFilter());
        myManager.requestGroupInfo(myChannel, new WifiP2pManager.GroupInfoListener() { // p2
            @Override
            public void onGroupInfoAvailable(WifiP2pGroup wifiP2pGroup) {
                //deviceListUpdate(wifiP2pGroup);
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(myBroadCastReceiver);
    }

    public void finish() {
        super.finish();

        // activity가 사라질 때 transition 효과 지정
        overridePendingTransition(android.R.anim.slide_in_left, R.anim.anim_slide_out_right);
    }

    @Override
    public void onDestroy() {
        removeGroup();
        myManager.requestGroupInfo(myChannel, new WifiP2pManager.GroupInfoListener() { // p2
            @Override
            public void onGroupInfoAvailable(WifiP2pGroup wifiP2pGroup) {
                deviceListUpdate(wifiP2pGroup);
            }
        });

        super.onDestroy();
    }

    public void destroyManual() {
        super.onDestroy();
    }

    @Override
    public void setIsWifiP2pEnabled(boolean enabled) {
        this.isWifiP2pEnabled = enabled;
    }

    /*
    BroadCastReceiver 가 WIFI_P2P_CONNECTION_CHANGED_ACTION intent 를 받았을 때 호출.
    새롭게 연결된 Client 가 있거나, 기존의 Client 가 제외되거나 그룹이 생성, 제거됐을 때 호출됨.
    Handshake Process 의 중추.
    p1 : myDeviceInfo 가 초기화 되지 않은 경우 초기화함.(wifiP2pInfo 를 통해 GroupOwner 인 자신의 IP 주소를 얻을 수 있음)
    p2 : WifiP2pGroup 을 얻어 Group 의 Client 의 WifiP2pDevice 정보를 얻을 수 있음 -> DeviceInfoList 갱신.
    p3 : Group 이 생성되있고, 자신이 GroupOwner 인 경우(Server 는 항상 GroupOwner) Handshake process 로 통신 시도.
        해당 AsyncTask 에서 수신된 데이터를 통해 새롭게 추가된 Client 의 IP address 와 Display 정보를 DeviceInfoList 에 추가함.
        onPostExecute() 를 통해 adapter 에 직접 notifyDataSetChanged()를 보내 ListView 를 최신화함.
    p4 : 그룹이 생성된 경우와 그렇지 않은 경우 btn 정보 변경.
     */


    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
        //btn_Server_Control.setEnabled(true);
        btn_File_Select.setEnabled(true);
        btn_File_Select.setAlpha(1f);
        if (log_on) {
            Log.e(TAG, "onConnectionInfoAvailable");
            Log.e(TAG, "onConnectionInfoAvailable groupFormed: " + wifiP2pInfo.groupFormed);
            Log.e(TAG, "onConnectionInfoAvailable isGroupOwner: " + wifiP2pInfo.isGroupOwner);
            Log.e(TAG, "onConnectionInfoAvailable getHostAddress: " + wifiP2pInfo.groupOwnerAddress.getHostAddress());
        }
        myWifiP2pInfo = wifiP2pInfo;

        if (myDeviceInfo == null) { // p1
            setMyDeviceInfo(wifiP2pInfo);
        }

        int tmpListSize = myDeviceInfoList.size();


        myManager.requestGroupInfo(myChannel, new WifiP2pManager.GroupInfoListener() { // p2
            @Override
            public void onGroupInfoAvailable(WifiP2pGroup wifiP2pGroup) {
                deviceListUpdate(wifiP2pGroup);
            }
        });


    }

    /*
    Wi-Fi P2P Connection 이 해제될 때 호출됨
     */
    @Override
    public void onDisconnection() {
        Log.e(TAG, "onDisconnection");
        myWifiP2pDeviceList.clear();
        //btn_Server_Control.setEnabled(false);
        isGroupExist = false;
        myDeviceInfoList.clear();
        myServerAdapter.notifyDataSetChanged();
    }

    /*
    BroadCastReceiver 가 WIFI_P2P_THIS_DEVICE_CHANGED_ACTION intent 를 받았을 때 호출됨
    별 의미는 없고 자신의 기기 정보를 받아 View 에 올려주는 기능을 하고 있음
     */
    @Override
    public void onSelfDeviceAvailable(WifiP2pDevice wifiP2pDevice) {
        if (log_on) {
            Log.e(TAG, "onSelfDeviceAvailable");
            Log.e(TAG, "DeviceName: " + wifiP2pDevice.deviceName);
            Log.e(TAG, "DeviceAddress: " + wifiP2pDevice.deviceAddress);
            Log.e(TAG, "Status: " + wifiP2pDevice.status);
        }
        txt_myDevice_Name.setText(wifiP2pDevice.deviceName);
        txt_myDevice_Address.setText(wifiP2pDevice.deviceAddress);
        //txt_myDevice_State.setText(getDeviceState(wifiP2pDevice.status));
        myWifiP2pDevice = wifiP2pDevice;
        if (!isGroupExist) {
            createGroup();
        }
    }

    /*
    BroadCastReceiver 가 WIFI_P2P_PEERS_CHANGED_ACTION intent 를 받았을 때 호출됨
    Server Activity 에서는 사용되지 않음
     */
    @Override
    public void onPeersAvailable(WifiP2pDeviceList wifiP2pDeviceList) {
        //Log.e(TAG, "onPeersAvailable : wifiP2pDeviceList.size : " + wifiP2pDeviceList.getDeviceList().size());
    }

    /*
    새로운 Wi-Fi P2P Group 생성
     */
    public void createGroup() {
        myManager.createGroup(myChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.v(TAG, "Create Group Success");
                //showToast("Create Group Success");
                isGroupExist = true;
            }

            @Override
            public void onFailure(int i) {
                Log.e(TAG, "Create Group Failed");
                //showToast("Create Group Failed :: " + i);
            }
        });
    }

    /*
    Wi-Fi P2P 그룹에서 탈퇴하는 기능
    Server Activity 는 항상 Group Owner로 동작하므로 그룹이 해산됨(예상)
     */
    public void removeGroup() {
        myManager.removeGroup(myChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.v(TAG, "Remove Group Success");
                //showToast("Remove Group Success");
                isGroupExist = false;
                myDeviceInfo = null;
                myDeviceInfoList.clear();
                myServerAdapter.notifyDataSetChanged();
            }

            @Override
            public void onFailure(int i) {
                Log.e(TAG, "Remove Group Failed");
                //showToast("Remove Group Failed :: " + i);
            }
        });
    }

    /*
    WifiP2pDevice.status 의 return value 가 int 이므로 String 으로 변환
     */
    public static String getDeviceState(int deviceState) {
        switch (deviceState) {
            case WifiP2pDevice.AVAILABLE:
                return "Available";
            case WifiP2pDevice.INVITED:
                return "Invited";
            case WifiP2pDevice.CONNECTED:
                return "Connected";
            case WifiP2pDevice.FAILED:
                return "Failed";
            case WifiP2pDevice.UNAVAILABLE:
                return "Unavailable";
            default:
                return "Error";
        }
    }

    public boolean isLocationServiceEnabled() {
        LocationManager locationManager = null;
        boolean gps_enabled = false, network_enabled = false;

        if (locationManager == null)
            locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        try {
            gps_enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception ex) {
            //do nothing...
        }

        try {
            network_enabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ex) {
            //do nothing...
        }

        return gps_enabled || network_enabled;

    }

    private int checkWifiOnAndConnected() {
        WifiManager wifiMgr = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        if (wifiMgr.isWifiEnabled()) { // Wi-Fi adapter is ON

            WifiInfo wifiInfo = wifiMgr.getConnectionInfo();

            if (wifiInfo.getNetworkId() == -1) {
                return 3; // Not connected to an access point
            }
            return 1; // Connected to an access point
        } else {
            return 2; // Wi-Fi adapter is OFF
        }
    }

    /*
    Wi-Fi P2P Peerlist를 받아오기 위해 android 일정 버전 이상에서는 ACCESS_FINE_LOCATION 권한을 요구함.
    해당 권한은 Dangerous Permission에 해당되므로 runtime 중에 권한을 요청하여 허가받아야함.
     */
    public void permissionCheck(int permission) {
        int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 0; // permission 1 : 정확한 위치 권한
        int MY_PERMISSIONS_REQUEST_CHANGE_WIFI_MULTICAST_STATE = 0; // permission 2 : 멀티캐스트 상태 권한
        int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 0; // permission 3 : 외부 저장소 읽기 권한
        int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 0; // permission 4 : 외부 저장소 쓰기 권한
        int MY_PERMISSIONS_REQUEST_MULTI = 0;

        ArrayList<String> permissions = new ArrayList<String>();
        if (permission == 0 || permission == 1) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_WIFI_STATE);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.CHANGE_WIFI_STATE);
            }
        }
        if (permission == 0 || permission == 2) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CHANGE_WIFI_MULTICAST_STATE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.CHANGE_WIFI_STATE);
            }
        }
        if (permission == 0 || permission == 3) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
        if (permission == 0 || permission == 4) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }
        if (!permissions.isEmpty()) {
            String[] requestPermissionsArray = new String[permissions.size()];
            requestPermissionsArray = permissions.toArray(requestPermissionsArray);
            ActivityCompat.requestPermissions(this, requestPermissionsArray, MY_PERMISSIONS_REQUEST_MULTI);
        }
        spinningDialog.cancel();
    }

    /*
    Server Activity 에서는 사용하지 않음
     */
    public void connect(final WifiP2pDevice d) { //Wifi P2P 연결
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = d.deviceAddress;
        config.groupOwnerIntent = 15;
        config.wps.setup = WpsInfo.PBC;
        if (d.status == WifiP2pDevice.CONNECTED) {
            return;
        }
        myManager.connect(myChannel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.v(TAG, "Connect Success");
                //showToast("Connect Success");
                DeviceInfo di = new DeviceInfo(d);
                myDeviceInfoList.add(di);
            }

            @Override
            public void onFailure(int i) {
                Log.e(TAG, "Connect Failed");
                //showToast("Connect Failed");
            }
        });
    }

    /*
    Server Activity 에서 WifiP2pGroup - DeviceInfoList 의 동기화를 위한 함수
    Group 의 ClientList 가 변경될 때마다 호출할 것을 권장
    Case 1 : Group 의 Client 수가 DeviceInfoList.size() 보다 큰 경우(새로운 클라이언트가 추가된 경우)
        새로운 Client 의 WifiP2pDevice 정보를 DeviceInfoList 에 add
    Case 2 : Group 의 Client 수가 DeviceInfoList.size() 보다 작은 경우(기존의 클라이언트가 제외된 경우)
        제외된 Client 의 DeviceInfo 정보를 List 에서 remove
    Case 3 : Group 의 Client 수가 DeviceInfoList.size() 와 같은 경우(그룹 정보가 유지되는 경우)
        현재 DeviceInfoList 의 정보와 Group 의 Client 정보가 일치하는지 확인 -> 불일치 시 Log.e
     */
    public void deviceListUpdate(WifiP2pGroup group) {
        if (group == null) { // nullPointException 방지
            myDeviceInfoList.clear();

            return;
        }
//        if (group.getClientList().size() == 0) {
//            btn_File_Transfer.setEnabled(false);
//            showToast("연결 가능한 기기가 없습니다.");
//            return;
//        } else
        if (myDeviceInfoList.size() < group.getClientList().size()) { //Case 1
            Log.v(TAG, "deviceListUpdate : Case 1");
            ArrayList<WifiP2pDevice> tempWifiP2pDeviceList = new ArrayList<>(group.getClientList());
            boolean exist = false;
            Log.v(TAG, "tempWifiP2pDeviceList.size() = " + tempWifiP2pDeviceList.size());
            for (int i = 0; i < tempWifiP2pDeviceList.size(); i++) {
                exist = false;
                for (int j = 0; j < myDeviceInfoList.size(); j++) {
                    if (myDeviceInfoList.get(j).getWifiP2pDevice().equals(tempWifiP2pDeviceList.get(i))) {
                        exist = true;
                        break;
                    }
                }
                if (!exist) {
                    DeviceInfo di = new DeviceInfo(tempWifiP2pDeviceList.get(i));
                    myDeviceInfoList.add(di);
                    Log.v(TAG, "added : " + tempWifiP2pDeviceList.get(i).deviceName);
                    callServerTask(MyServerTask.SERVER_HANDSHAKE_SERVICE);
                    return;
                }
            }
        } else if (myDeviceInfoList.size() > group.getClientList().size()) { // Case 2
            Log.v(TAG, "deviceListUpdate : Case 2");
            ArrayList<WifiP2pDevice> tempWifiP2pDeviceList = new ArrayList<>(group.getClientList());
            boolean exist = false;
            if (group.getClientList().size() == 0) {
                myDeviceInfoList.clear();
            }
            for (int i = 0; i < myDeviceInfoList.size(); i++) {
                exist = false;
                for (int j = 0; j < tempWifiP2pDeviceList.size(); j++) {
                    if (myDeviceInfoList.get(i).getWifiP2pDevice().equals(tempWifiP2pDeviceList.get(j))) {
                        exist = true;
                        break;
                    }
                    if (!exist) {
                        Log.v(TAG, myDeviceInfoList.get(i).getWifiP2pDevice().deviceName + " disconnected");
                        myDeviceInfoList.remove(i);
                        myServerAdapter.notifyDataSetChanged();
                    }
                }
            }
            return;
        } else if (myDeviceInfoList.size() == group.getClientList().size()) { // Case 3
            Log.v(TAG, "deviceListUpdate : Case 3 with size : " + myDeviceInfoList.size());
            if (myDeviceInfoList.size() > 0) {
                ArrayList<WifiP2pDevice> tempWifiP2pDeviceList = new ArrayList<>(group.getClientList());
                int test = 0;
                for (int i = 0; i < myDeviceInfoList.size(); i++) {
                    for (int j = 0; j < tempWifiP2pDeviceList.size(); j++) {
                        if (myDeviceInfoList.get(i).getWifiP2pDevice().equals(tempWifiP2pDeviceList.get(j))) {
                            test++;
                            break;
                        }
                    }
                }
                if (myDeviceInfoList.size() != test) {
                    Log.e(TAG, "Device info list doesn't matched");
                }
            }
            return;
        }
    }


    public void setMyDeviceInfo(WifiP2pInfo wifiP2pInfo) {
        String brandName = Build.BRAND;
        String modelName = Build.MODEL;
        DisplayMetrics dm = this.getResources().getDisplayMetrics();
        int width = dm.widthPixels;
        int height = dm.heightPixels;
        int densityDpi = dm.densityDpi;
        boolean isGroupOwner = true;
        myDeviceInfo = new DeviceInfo(myWifiP2pDevice, brandName, modelName, wifiP2pInfo.groupOwnerAddress.getHostAddress(), width, height, densityDpi, isGroupOwner, dm);
        myDeviceInfo.convertPx();
        Log.v(TAG, "serverDeviceInfo : " + myDeviceInfo.getString());
    }

    @Override
    public void onProgressFinished() {

    }

    @Override
    public void onRcvClickOK(int state) {
    }

    @Override
    public void onRcvClickCancel(int state) {

    }

    @Override
    public void onSendClickOK(int state) {
        if (state == SendDialog.SEND_DLG_INIT) {
            nowTask = callServerTask(MyServerTask.SERVER_TCP_FILE_TRANSFER_SERVICE);
        }
    }

    @Override
    public void onSendClickCancel(int state) {
        if (nowTask != null) {
            nowTask.cancel(true);
            nowTask = null;
        }
        sendDialog.cancel();
    }

    @Override
    public void onAllProgressFinished() {
        sendDialog.cancel();
        nowTask = null;
    }

    @Override
    public void progressUpdate(int progress) {
        sendDialog.setProgress(progress);
    }

    @Override
    public void onSendFinished() {
        sendDialog.sendCompleteOne();
        myServerAdapter.notifyDataSetChanged();
    }

    @Override
    public void onHandshaked() {
        sendDialog.setSending();

    }

    @Override
    public void onAllSendFinished() {
        Log.v(TAG, "onAllSendFinished()");
        sendDialog.cancel();
        boolean allHasVideo = true;
        for (DeviceInfo di : myDeviceInfoList) {
            if (!di.isHasVideo()) {
                allHasVideo = false;
            }
        }
        if (allHasVideo) {
            DeviceInfoListUtil dilu = new DeviceInfoListUtil(myDeviceInfoList, myDeviceInfo, videoPath, getApplicationContext().getResources().getDisplayMetrics());
            dilu.processList();
            processedDIL = dilu.getResultList();
            for (DeviceInfo di : processedDIL) {
                Log.v(TAG, di.getWifiP2pDevice().deviceName + " : " + di.getLongString());
                if (di.getWifiP2pDevice().deviceAddress.equals(myDeviceInfo.getWifiP2pDevice().deviceAddress)) {
                    myDeviceInfo = di;
                }
            }
            Log.v(TAG, "prcessed serverDeviceInfo : " + myDeviceInfo.getLongString());
        }
    }

    @Override
    public void onWaiting() {
        sendDialog.setWaiting();
    }

    @Override
    public void onCancelTransfer() {
        sendDialog.cancel();
        showToast("영상 전송이 취소되었습니다");
    }

    @Override
    public void onNotify() {
        myServerAdapter.notifyDataSetChanged();
    }

    @Override
    public void onShowGuidelineSended() {
        guideLineDialog.onClientsReady();
    }

    @Override
    public void onPreparePlay() {
        guideLineDialog.cancel();
        nowTask = null;

        Intent intent = new Intent(this, PlayerHost.class);
        Log.v(TAG, "Before parcel : " + myDeviceInfo.getLongString());
        intent.putExtra("myDeviceInfo", myDeviceInfo);
        startActivityForResult(intent, VIDEO_PLAYER_RESULT_CODE);
    }

    @Override
    public void onClickOkButton() {
        nowTask = callServerTask(MyServerTask.SERVER_TCP_PREPARE_PLAY_SERVICE);
    }

    @Override
    public void onStartPlayer() {
        nowTask = null;
    }

    @Override
    public void onReceiveConPause() {

    }

    @Override
    public void onReceiveConPlay() {

    }

    @Override
    public void onReceiveConStop() {

    }

    @Override
    public void onReceiveConSeek() {

    }
}
