package edu.buffalo.cse.cse486586.simpledynamo;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Formatter;
import java.util.LinkedList;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

//--------------------------------------------------------------------------------------------------------------------------------------------------

class MissedObject{

	String key = null;
	String value = null;
	String missedHash = null;
	String type = null;
	Boolean check = null;

	public MissedObject(String key, String value, String missedHash, String type, Boolean check){
		this.key = key;
		this.value = value;
		this.missedHash = missedHash;
		this.type = type;
		this.check = check;
	}

}

//--------------------------------------------------------------------------------------------------------------------------------------------------

public class SimpleDynamoProvider extends ContentProvider {
	static final int SERVER_PORT = 10000;
	static final String TAG = SimpleDynamoProvider.class.getName();
	static final String REMOTE_PORT0 = "11108";
	static final String REMOTE_PORT1 = "11112";
	static final String REMOTE_PORT2 = "11116";
	static final String REMOTE_PORT3 = "11120";
	static final String REMOTE_PORT4 = "11124";

	static final LinkedList<String> REMOTE_PORTS = new LinkedList<String>();
	static final LinkedList<String> HashList = new LinkedList<String>();
	static final LinkedList<String> storedOriginalKeys = new LinkedList<String>();
	static String portStr;
	static String myPort;
	static String myHash = null;

	static String headChord;
	static int tempCount1 = 0;
	static int tempCount2 = 0;
	static LinkedList<String> indexList = new LinkedList<String>();
	LinkedList<MissedObject> missedList = new LinkedList<MissedObject>();
	static String successor;
	static String predecessor;

//--------------------------------------------------------------------------------------------------------------------------------------------------

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {

		String hash_key = null;
		try {
			hash_key = genHash(selection);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}

		for(int i=0;i<HashList.size();i++){
			if((HashList.get(i).equals(headChord)) &&
					(hash_key.compareTo(HashList.get(i))<0 || hash_key.compareTo(HashList.get(HashList.size()-1))>0)){
				sendTo(i, selection, "delete",i+1);  //Sending to correct node along with the other two nodes which are replicas
				sendTo(i+1, selection, "delete",i+2);
				sendTo(i+2, selection, "delete",i+1);
			}else if(hash_key.compareTo(HashList.get(i))<0 && hash_key.compareTo(HashList.get(i-1))>0){
				if(i<HashList.size()-2){
					sendTo(i, selection, "delete",i+1);  //Sending to correct node along with the other two nodes which are replicas
					sendTo(i+1, selection, "delete",i+2);
					sendTo(i+2, selection, "delete",i+1);
				} else if(i==HashList.size()-2){
					sendTo(i, selection, "delete",i+1);  //Sending to correct node along with the other two nodes which are replicas
					sendTo(i+1, selection, "delete",0);
					sendTo(0, selection, "delete",i+1);
				} else if(i==HashList.size()-1){
					sendTo(i, selection, "delete",0);
					sendTo(0, selection, "delete",1);
					sendTo(1, selection, "delete",0);
				}
			}
		}

		return 0;
	}

//--------------------------------------------------------------------------------------------------------------------------------------------------

	@Override
	public String getType(Uri uri) {
		// TODO Auto-generated method stub
		return null;
	}

//--------------------------------------------------------------------------------------------------------------------------------------------------

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		String filename = values.get("key").toString();
		String hash_key = null;
		try {
			hash_key = genHash(filename);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		Log.i(TAG,"Original Key :"+filename);
		Log.i(TAG,"HASH_KEY :"+hash_key);
		Log.i(TAG,"My HASH :"+myHash);

		for(int i=0;i<HashList.size();i++){
			if((HashList.get(i).equals(headChord)) &&
					(hash_key.compareTo(HashList.get(i))<0 || hash_key.compareTo(HashList.get(HashList.size()-1))>0)){
				String data = values.get("value").toString();
				int r1 = i+1;
				int r2 = i+2;
				sendTo(i, filename, data+":"+i+":"+r1+":"+r2,i+1);  //Sending to correct node along with the other two nodes which are replicas
				sendTo(i+1, filename, data+":"+i+":"+r1+":"+r2,i+2);
				sendTo(i+2, filename, data+":"+i+":"+r1+":"+r2,i+1);
			}else if(hash_key.compareTo(HashList.get(i))<0 && hash_key.compareTo(HashList.get(i-1))>0){
				String data = values.get("value").toString();
				if(i<HashList.size()-2){
					int r1 = i+1;
					int r2 = i+2;
					sendTo(i, filename, data+":"+i+":"+r1+":"+r2,i+1);  //Sending to correct node along with the other two nodes which are replicas
					sendTo(i+1, filename, data+":"+i+":"+r1+":"+r2,i+2);
					sendTo(i+2, filename, data+":"+i+":"+r1+":"+r2,i+1);
				} else if(i==HashList.size()-2){
					int r1 = i+1;
					sendTo(i, filename, data+":"+i+":"+r1+":"+0,i+1);  //Sending to correct node along with the other two nodes which are replicas
					sendTo(i+1, filename, data+":"+i+":"+r1+":"+0,0);
					sendTo(0, filename, data+":"+i+":"+r1+":"+0,i+1);
				} else if(i==HashList.size()-1){
					sendTo(i, filename, data+":"+i+":"+0+":"+1,0);
					sendTo(0, filename, data+":"+i+":"+0+":"+1,1);
					sendTo(1, filename, data+":"+i+":"+0+":"+1,0);
				}
			}
		}

		return null;
	}

//--------------------------------------------------------------------------------------------------------------------------------------------------

	@Override
	public boolean onCreate() {

		REMOTE_PORTS.add(REMOTE_PORT0);
		REMOTE_PORTS.add(REMOTE_PORT1);
		REMOTE_PORTS.add(REMOTE_PORT2);
		REMOTE_PORTS.add(REMOTE_PORT3);
		REMOTE_PORTS.add(REMOTE_PORT4);

		addHashes();
		for(int i=0;i<HashList.size();i++){
			indexList.add(REMOTE_PORTS.get(i)+":"+HashList.get(i));
		}
		Collections.sort(HashList);
		REMOTE_PORTS.clear();

		for(int i=0;i<HashList.size();i++){
			for(int j=0;j<indexList.size();j++){
				String[] split = indexList.get(j).split(":");
				if(split[1].equals(HashList.get(i))){
					REMOTE_PORTS.add(split[0]);
				}
			}
		}

		headChord = HashList.get(0);
		for(int i=0;i<HashList.size();i++){
			Log.e("After sorting ",HashList.get(i));
		}

		Context context = getContext();
		TelephonyManager tel = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
		myPort = String.valueOf((Integer.parseInt(portStr) * 2));  //portStr is emulator name while myPort is the port number

		for(int i=0;i<REMOTE_PORTS.size();i++){
			if(REMOTE_PORTS.get(i).equals(myPort) && i==0){
				predecessor = REMOTE_PORTS.get(4);
				successor = REMOTE_PORTS.get(i+1);
			} else if(REMOTE_PORTS.get(i).equals(myPort) && i==4){
				predecessor = REMOTE_PORTS.get(i-1);
				successor = REMOTE_PORTS.get(0);
			} else if(REMOTE_PORTS.get(i).equals(myPort)){
				predecessor = REMOTE_PORTS.get(i-1);
				successor = REMOTE_PORTS.get(i+1);
			}
		}

		Log.e(TAG,"My ID "+portStr);

		try {
			myHash = genHash(portStr);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}

		try {
			ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
			Log.i(TAG,"Server socket created");
			new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);

		} catch (IOException e) {
			Log.e(TAG, "Can't create a ServerSocket");
		}

		String result = null;  //Querying to check whether recovery or initial start
		try {
			result = queryFunction("1");
		} catch (IOException e) {
			e.printStackTrace();
		}

		if(result!=null){ //If 1 already present it means its a recovery
			Log.e(TAG,"EUREKA!!"); //Here we write code for recovery
			askRecovery();
		} else{
			FileOutputStream fos = null; //Inserting at the start and also at recovery
			try {
				fos = context.openFileOutput("1", Context.MODE_PRIVATE);
				fos.write("Initial write".getBytes());
				Log.i("Insert","Inserted 1");
				fos.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return false;
	}

//--------------------------------------------------------------------------------------------------------------------------------------------------

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
						String[] selectionArgs, String sortOrder) {

		Context context = getContext();
		String[] columns = new String[]{"key","value"};
		MatrixCursor mc = new MatrixCursor(columns);
		Log.i("Query",selection);
		if(selection.equals("@")){
			mc = localDump(context, mc);
			Log.i(TAG,"LOCAL DUMP "+storedOriginalKeys.size());
			return mc;
		} else if(selection.equals("*")){
			for(int i=0;i<HashList.size();i++){
				Log.i(TAG,"STAR QUERY TO "+REMOTE_PORTS.get(i)+"  "+HashList.get(i));
				String result = sendStarQuery(i);
				if(result == null)
					Log.i(TAG,"DEAD AVD");
				else if(result.contains("Empty"))
					Log.i(TAG,"Empty");
				else{
					Log.i(TAG,"Result "+result.split(":").length);
					String[] resultArray = result.split(":");
					for(int j=0;j<resultArray.length-1;j+=2){
						mc.addRow(new String[] { resultArray[j], resultArray[j+1] });
					}
				}
			}
			return mc;
		}

		String hash_key = null;
		try {
			hash_key = genHash(selection);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}

		for(int i=0;i<HashList.size();i++){
			if((HashList.get(i).equals(headChord)) &&
					(hash_key.compareTo(HashList.get(i))<0 || hash_key.compareTo(HashList.get(HashList.size()-1))>0)){
				String result1 = sendQuery(i, selection);
				String result2 = sendQuery(i+1, selection);
				String result3 = sendQuery(i+2, selection);
				mc = queryCalc(result1, result2, result3);
				return mc;

			}else if(hash_key.compareTo(HashList.get(i))<0 && hash_key.compareTo(HashList.get(i-1))>0){
				if(i==4){
					String result1 = sendQuery(i, selection);
					String result2 = sendQuery(0, selection);
					String result3 = sendQuery(1, selection);
					mc = queryCalc(result1, result2, result3);

				} else if (i==3){
					String result1 = sendQuery(i, selection);
					String result2 = sendQuery(i+1, selection);
					String result3 = sendQuery(0, selection);
					mc = queryCalc(result1, result2, result3);

				}
				else {
					String result1 = sendQuery(i, selection);
					String result2 = sendQuery(i+1, selection);
					String result3 = sendQuery(i+2, selection);
					mc = queryCalc(result1, result2, result3);

				}
				return mc;
			}
		}

		return null;
	}

//--------------------------------------------------------------------------------------------------------------------------------------------------

	@Override
	public int update(Uri uri, ContentValues values, String selection,
					  String[] selectionArgs) {
		// TODO Auto-generated method stub
		return 0;
	}

//--------------------------------------------------------------------------------------------------------------------------------------------------

	private String genHash(String input) throws NoSuchAlgorithmException {
		MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
		byte[] sha1Hash = sha1.digest(input.getBytes());
		Formatter formatter = new Formatter();
		for (byte b : sha1Hash) {
			formatter.format("%02x", b);
		}
		return formatter.toString();
	}

//--------------------------------------------------------------------------------------------------------------------------------------------------

	private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

		@Override
		protected Void doInBackground(ServerSocket... sockets) {
			ServerSocket serverSocket = sockets[0];
			Log.e(TAG, "Server Task");
            /*
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */

			for (int i = 0; i < 9999; i++) {       //This loop ensures that messages can be sent 9999 times on both sides
				try {
					Socket s1 = serverSocket.accept();
					DataOutputStream dOS = new DataOutputStream(s1.getOutputStream());
					DataInputStream dIS = new DataInputStream(s1.getInputStream());
					String message = dIS.readUTF();
					String[] array = message.split(":");

					if(array[0].equals("*")){  //Dump all contents of the node to the one calling for *
						Log.e(TAG,"RECEIVED STAR REQUEST");
						Context context = getContext();
						String[] columns = new String[]{"key","value"};
						MatrixCursor mc = new MatrixCursor(columns);
						mc = localDump(context, mc);
						String result = "";
						if (mc.getCount()!=0) {
							mc.moveToFirst();
							do {
								for (int j = 0; j < mc.getColumnCount(); j++) {
									result += mc.getString(j)+":";
								}
							}while (mc.moveToNext());
							dOS.writeUTF(result);
						} else{
							dOS.writeUTF("Empty");
						}

					} else if(array[0].equals("Checking")){
						Log.i(TAG,"SERVER TASK ENTERED");
						Context context = getContext();
						String[] columns = new String[]{"key","value"};
						MatrixCursor mc = new MatrixCursor(columns);
						mc = localDump(context, mc);
						if(mc.getCount()==0)
							dOS.writeUTF("ack");
						else
							dOS.writeUTF("recovery");

					} else if(array.length==1){  //Querying and sending back reply
						String queryResult = queryFunction(array[0]);
						dOS.writeUTF(queryResult);

					} else if(array[0].equals("Recovery ping")){
						String kvPair = "";
						if(!missedList.isEmpty()){
							for(int j=0;j<missedList.size();j++){
								if(array[1].equals(missedList.get(j).missedHash) && missedList.get(j).check == false){
									String key = missedList.get(j).key;
									String value = missedList.get(j).value;
									String type = missedList.get(j).type;
									kvPair += key+":"+value+":"+type+":";
									missedList.get(j).check = true;
								}
							}
							if(kvPair.split(":").length==1)
								dOS.writeUTF("Ack");
							else
								dOS.writeUTF(kvPair);
						} else{
							dOS.writeUTF("Ack");
						}

					} else if(array[0].equals("INSERT IN HASH")){
						Log.e(TAG,"INSERT IN LIST FOR "+array[1]);
						String filename = array[1];
						String data = array[2];
						String mainIndex = array[3];
						String type = array[4];
						MissedObject mo = new MissedObject(filename, data, mainIndex, type, false);
						missedList.add(mo);

					} else if(array[1].equals("delete")){
						deleteFunction(array[0]);
						String ack = "Ack";
						dOS.writeUTF(ack);

					} else if(array.length==2){
						Log.e(TAG,"REACHED");
						dOS.writeUTF("Ack");

					} else if(array.length==5){  //Original message comes to node (insert)
						ContentValues keyValueToInsert = new ContentValues();
						keyValueToInsert.put("key",array[0]);
						keyValueToInsert.put("value",array[1]);
						insertFunction(array[0], keyValueToInsert);
						String ack = "Ack";
						dOS.writeUTF(ack);
					}

					dOS.flush();
					dIS.close();
					dOS.close();

				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			return null;
		}
	}

//--------------------------------------------------------------------------------------------------------------------------------------------------

	private class ClientTask extends AsyncTask<String, Void, Void> {

		@Override
		protected Void doInBackground(String... msgs) {

			LinkedList<String> recoverList = new LinkedList<String>();
			recoverList.add(predecessor);
			recoverList.add(successor);

			for(String REMOTE_PORT : recoverList){
				try {
					Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
							Integer.parseInt(REMOTE_PORT));

					DataInputStream dIS = new DataInputStream(socket.getInputStream());
					DataOutputStream dOut = new DataOutputStream(socket.getOutputStream());

					Log.i(TAG,"Sending recovery message to "+REMOTE_PORT);

					dOut.writeUTF("Recovery ping:"+myHash);
					String result = dIS.readUTF();

					if(!result.equals("Ack")){
						String[] replicaArray = result.split(":");
						for(int i=0;i<replicaArray.length;i+=3){
							if(replicaArray[2].equals("Insert")){
								tempCount2++;
								Log.i(TAG,"Recovered Insert from "+REMOTE_PORT+"  Recovery "+tempCount2);
								ContentValues keyValueToInsert = new ContentValues();
								keyValueToInsert.put("key",replicaArray[i]);
								keyValueToInsert.put("value",replicaArray[i+1]);
								insertFunction(replicaArray[i], keyValueToInsert);
							} else if(replicaArray[2].equals("Delete")){
								Log.i(TAG,"Recovered Delete");
								deleteFunction(replicaArray[i]);
							}
						}

					}

					dOut.flush();
					dIS.close();
					dOut.close();

				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			return null;
		}
	}

//--------------------------------------------------------------------------------------------------------------------------------------------------

	public void addHashes(){
		try {
			String hash0 = genHash("5554");
			String hash1 = genHash("5556");
			String hash2 = genHash("5558");
			String hash3 = genHash("5560");
			String hash4 = genHash("5562");

			HashList.add(hash0);
			HashList.add(hash1);
			HashList.add(hash2);
			HashList.add(hash3);
			HashList.add(hash4);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
	}

//--------------------------------------------------------------------------------------------------------------------------------------------------

	public MatrixCursor localDump(Context context, MatrixCursor mc){
		String[] FileList = context.fileList();
		for(int i=0; i<FileList.length; i++){
			try {
				String selection = FileList[i];
				if(selection.equals("1"))
					continue;
				FileInputStream fIS = context.openFileInput(selection);
				InputStreamReader iSR = new InputStreamReader(fIS);
				BufferedReader bReader = new BufferedReader(iSR);
				String finalS = bReader.readLine();
				mc.addRow(new String[] { selection, finalS });
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return  mc;
	}

//--------------------------------------------------------------------------------------------------------------------------------------------------

	public void insertFunction(String filename, ContentValues values){
		String data = values.get("value").toString();
		Context context = getContext();
		try{
			FileOutputStream fos = context.openFileOutput(filename, Context.MODE_PRIVATE);
			storedOriginalKeys.add(filename);
			fos.write(data.getBytes());
			fos.close();
			Log.i(TAG,"Inserted key "+filename+" value "+data);
		}catch(IOException e){
			e.printStackTrace();
		}
	}

//--------------------------------------------------------------------------------------------------------------------------------------------------

	public void sendTo(int index, String filename, String data, int replicaNum){
		Log.i(TAG, "Send to ");
		String portNum = REMOTE_PORTS.get(index);
		Log.i(TAG,portNum);
		try {
			Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
					Integer.parseInt(portNum));

			DataInputStream dIS = new DataInputStream(socket.getInputStream());
			DataOutputStream dOut = new DataOutputStream(socket.getOutputStream());

			dOut.writeUTF(filename+":"+data);
			dIS.readUTF();

			dOut.flush();
			dIS.close();
			dOut.close();

		} catch (IOException e) {
			String[] dataArr = data.split(":");
			if(data.split(":").length>1){
				tempCount1++;
				Log.e(TAG, "MISSED "+tempCount1);
				Log.e(TAG,"SENT MISSED TO "+REMOTE_PORTS.get(replicaNum));
				sendReplicaNum(filename, dataArr[0], HashList.get(index),"Insert" ,replicaNum);
			} else{
				Log.e(TAG, "MISSED");
				Log.e(TAG,"SENT MISSED TO "+REMOTE_PORTS.get(replicaNum));
				sendReplicaNum(filename, dataArr[0], HashList.get(index),"Delete" ,replicaNum);
			}
		}

	}

//--------------------------------------------------------------------------------------------------------------------------------------------------

	public String sendQuery(int index, String selection){
		String portNum = REMOTE_PORTS.get(index);
		Log.i("Send query to",portNum);
		try {
			Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
					Integer.parseInt(portNum));

			DataInputStream dIS = new DataInputStream(socket.getInputStream());
			DataOutputStream dOut = new DataOutputStream(socket.getOutputStream());

			dOut.writeUTF(selection);
			String result = dIS.readUTF();

			dOut.flush();
			dIS.close();
			dOut.close();

			return  result;

		} catch (IOException e) {
			Log.e(TAG,"MISSED QUERY");
		}
		return  null;
	}

//--------------------------------------------------------------------------------------------------------------------------------------------------

	public String queryFunction(String selection) throws IOException {
		Context context = getContext();
		FileInputStream fIS = context.openFileInput(selection);
		InputStreamReader iSR = new InputStreamReader(fIS);
		BufferedReader bReader = new BufferedReader(iSR);
		String finalS = bReader.readLine();
		return selection+":"+finalS;
	}

//--------------------------------------------------------------------------------------------------------------------------------------------------

	public String sendStarQuery(int index){
		String portNum = REMOTE_PORTS.get(index);
		try {
			Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
					Integer.parseInt(portNum));

			DataInputStream dIS = new DataInputStream(socket.getInputStream());
			DataOutputStream dOut = new DataOutputStream(socket.getOutputStream());

			dOut.writeUTF("*");
			String result = dIS.readUTF();

			dOut.flush();
			dIS.close();
			dOut.close();

			return  result;

		} catch (IOException e) {
			Log.e(TAG,"MISSED STAR QUERY");
		}
		return null;
	}

//--------------------------------------------------------------------------------------------------------------------------------------------------

	public void deleteFunction(String filename){
		Log.i(TAG, "Deleting "+filename);
		Context context = getContext();
		context.deleteFile(filename); //Deleting from the database and also from the replicas
	}

//--------------------------------------------------------------------------------------------------------------------------------------------------

	public void askRecovery(){
		new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
	}

//--------------------------------------------------------------------------------------------------------------------------------------------------

	public MatrixCursor queryCalc(String result1, String result2, String result3){
		String[] columns = new String[]{"key","value"};
		MatrixCursor mc = new MatrixCursor(columns);
		if(result1==null){
			String[] array = result2.split(":");
			mc.addRow(new String[] { array[0], array[1] });
			Log.i(TAG,"Retrieved "+array[0]+" "+array[1]);
		} else if(result2==null || result3==null){
			String[] array = result1.split(":");
			mc.addRow(new String[] { array[0], array[1] });
			Log.i(TAG,"Retrieved "+array[0]+" "+array[1]);
		} else if(result1.equals(result2) && result1.equals(result3)){
			String[] array = result1.split(":");
			mc.addRow(new String[] { array[0], array[1] });
			Log.i(TAG,"Retrieved "+array[0]+" "+array[1]);
		} else if((result1.equals(result2) && !result1.equals(result3)) || (result1.equals(result3) && !result1.equals(result2)) ){
			String[] array = result1.split(":");
			mc.addRow(new String[] { array[0], array[1] });
			Log.i(TAG,"Retrieved "+array[0]+" "+array[1]);
		} else if(result2.equals(result3) && !result2.equals(result1)){
			String[] array = result2.split(":");
			mc.addRow(new String[] { array[0], array[1] });
			Log.i(TAG,"Retrieved "+array[0]+" "+array[1]);
		}
		return mc;
	}

//--------------------------------------------------------------------------------------------------------------------------------------------------

	public void sendReplicaNum(String filename, String data, String mainIndex, String type, int replicaNum){
		String portNum = REMOTE_PORTS.get(replicaNum);
		try {
			Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
					Integer.parseInt(portNum));

			DataInputStream dIS = new DataInputStream(socket.getInputStream());
			DataOutputStream dOut = new DataOutputStream(socket.getOutputStream());

			dOut.writeUTF("INSERT IN HASH:"+filename+":"+data+":"+mainIndex+":"+type+":"+replicaNum);
			dIS.readUTF();
			Log.i(TAG,"SUCCESSFULLY INSERTED RECOVERY MESSAGE IN "+portNum);

			dOut.flush();
			dIS.close();
			dOut.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}

//--------------------------------------------------------------------------------------------------------------------------------------------------