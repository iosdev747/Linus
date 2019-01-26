package core;

import ai.api.AIConfiguration;
import ai.api.AIDataService;
import ai.api.model.AIRequest;
import ai.api.model.AIResponse;
import com.google.gson.JsonElement;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXTextField;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringJoiner;

public class Controller {

    @FXML
    private Label ipLabel;

    @FXML
    private Label pwdLabel;

    @FXML
    private Label cpuLabel;

    @FXML
    private Label ramLabel;

    @FXML
    private JFXButton goButton;

    @FXML
    private JFXTextField inputField;

    @FXML
    private JFXListView list;

    private boolean isProcessing = false;
    private Thread voiceThread;

    @FXML
    public void onGoAction(ActionEvent actionEvent) {
        System.out.println("action Running");
        if(isProcessing) {
            isProcessing = !isProcessing;
            if(voiceThread.isAlive()) {
                voiceThread.stop();
            }
            if(inputField.getText().equals("")) {
                inputField.setText(DAO.voiceOutput);
                if(inputField.getText().equals("")) {
                    return;
                }
            }
            AIConfiguration configuration = new AIConfiguration("c3a31db2f9bc467abebad1e364b8ff9f");

            AIDataService dataService = new AIDataService(configuration);
            System.out.print("START ");
            try {
                AIRequest request = new AIRequest(inputField.getText());
                //random unique sessionID
                request.setSessionId("1337");
                AIResponse response = dataService.request(request);
                System.out.println("\nSend Request: "+inputField.getText());
                System.out.println("session:"+response.getSessionId());
                if (response.getStatus().getCode() == 200) {
                    String action = response.getResult().getAction();
                    System.out.println("Action: "+action);
                    System.out.println("Speech: "+response.getResult().getFulfillment().getSpeech());
                    if(!response.getResult().getFulfillment().getSpeech().isEmpty()){
                        list.getItems().add(response.getResult().getFulfillment().getSpeech());
                    }else{
                        list.getItems().add(response.getResult().getAction());
                        HashMap<String, JsonElement> hashMap = response.getResult().getParameters();
                        Object[] arrays = hashMap.keySet().toArray();
                        for (Object o:arrays) {
                            System.out.println("" + hashMap.get(o));
                            list.getItems().add(o+":"+hashMap.get(o));
                        }
                        //if action equals to "cda" or "cdr" change pwd variable
                        if(response.getResult().getAction().equals("cda") || response.getResult().getAction().equals("cdr")) {
                            StringJoiner joiner = new StringJoiner("/");
                            for (Object o:hashMap.keySet().toArray()) {
                                joiner.add(hashMap.get(o).toString()).add("/");
                            }
                            //generate pwd from output from dialogflow
                            DAO.pwd = Paths.get(DAO.pwd.toString().concat(joiner.toString()));
                            return;
                        }
                        System.out.println("output command:" + response.getResult().getAction());
                        runPython(response.getResult().getAction(), hashMap);
                    }
                } else {
                    System.err.println(response.getStatus().getErrorDetails());
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            goButton.setText("Go");
        }
        else {
            isProcessing = !isProcessing;
            //setting GOOGLE_APPLICATION_CREDENTIALS variable
            setEnv("GOOGLE_APPLICATION_CREDENTIALS", "/home/iosdev747/Downloads/creds.json");
            voiceThread = new Thread(new Recognizer());
            voiceThread.start();
            goButton.setText("Stop");
        }
    }

    /**
     * Method to run python command which is connected through dialogflow.
     * @param action action to be taken
     * @param hashMap parameters for taking that action
     */
    public void runPython(String action, HashMap<String, JsonElement> hashMap) {
        StringBuilder str = new StringBuilder("python "+DAO.pythonPath+" " + action + " ");
        for (Object o:hashMap.keySet().toArray()) {
            str.append(o).append(":").append(hashMap.get(o).toString().replace(' ', '#')).append(",");
        }
        str.append("pwd").append(":").append(DAO.pwd.toString());
        /*
        sample output
        String str = "python /home/abcd/test/main.py act1 Name1:Value1,Name2:Value2,Name3:Value3,pwd:path";
         */
        try {
            String command = str.toString();
            Process proc = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line = "";
            while((line = reader.readLine()) != null) {
                System.out.print(line + "\n");
            }
            try {
                proc.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Method to set Environment Variable
     * @param key environment variable name
     * @param value environment variable value
     */
    public static void setEnv(String key, String value) {
        try {
            Map<String, String> env = System.getenv();
            Class<?> cl = env.getClass();
            Field field = cl.getDeclaredField("m");
            field.setAccessible(true);
            Map<String, String> writableEnv = (Map<String, String>) field.get(env);
            writableEnv.put(key, value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to set environment variable", e);
        }
    }

    public void updateGUI() {
        System.out.println("updating GUI");
        VBox vBox = new VBox();
        Output output = DAO.output;
        Iterator imageIterator = output.Image.iterator();
        Iterator uriIterator = output.URI.iterator();
        Iterator textIterator = output.Text.iterator();
        String name = output.getTitle();
        list.getItems().add(name);
        for (int i:DAO.output.getOrder()) {
            if(i == Constant.IMAGE) {
                System.out.println("image");
                core.template.Image image = ((core.template.Image)imageIterator.next());
                javafx.scene.image.Image sceneimage = new javafx.scene.image.Image(image.getSrc());
                ImageView imageView = new ImageView(sceneimage);
                if(sceneimage.getWidth() >= Constant.MAXWIDTH || sceneimage.getHeight() >= Constant.MAXHEIGHT) {
                    sceneimage = new javafx.scene.image.Image(image.getSrc(), Constant.MAXWIDTH, Constant.MAXHEIGHT, true, false);
                    imageView = new ImageView(sceneimage);
                }
                vBox.getChildren().add(imageView);
            } else if(i == Constant.URL) {
                System.out.println("url");
                vBox.getChildren().add(new Label(((core.template.URI)uriIterator.next()).getUrl()));
            } else if(i == Constant.TEXT) {
                System.out.println("text");
                vBox.getChildren().add(new Label(((core.template.Text)textIterator.next()).getData()));
            }
        }
        list.getItems().add(vBox);
    }

}
