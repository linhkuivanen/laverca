package fi.laverca.samples;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.LinkedList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bouncycastle.util.encoders.Base64;
import org.etsi.uri.TS102204.v1_1_2.Service;

import fi.laverca.FiComAdditionalServices;
import fi.laverca.FiComAdditionalServices.PersonIdAttribute;
import fi.laverca.FiComClient;
import fi.laverca.FiComRequest;
import fi.laverca.FiComResponse;
import fi.laverca.FiComResponseHandler;
import fi.laverca.JvmSsl;

public class SignText {

	private static final Log log = LogFactory.getLog(SignText.class);
	private static FiComRequest req;
	protected static SignTextProggerssBarUpdater callStateProgressBarUpdater = new SignTextProggerssBarUpdater();
	protected static int amountOfCalls = 0; 
	
	/**
	 * Connects to MSSP using SSL and waits for response
	 * @param phoneNumber
	 * @param textToBeSigned
	 */
	
	private static void estamblishConnection(final String phoneNumber, final String textToBeSigned) {
		
		log.info("setting up ssl");
		JvmSsl.setSSL("etc/laverca-truststore",
                "changeit",
                "etc/laverca-keystore",
                "changeit",
                "JKS");
		
		String apId  = "http://laverca-eval.fi";
        String apPwd = "pfkpfk";

        String aeMsspIdUri = "http://dev-ae.mssp.dna.fi";
        //TODO: TeliaSonera
        //TODO: Elisa

        String msspSignatureUrl    = "https://dev-ae.mssp.dna.fi/soap/services/MSS_SignaturePort";
        String msspStatusUrl       = "https://dev-ae.mssp.dna.fi/soap/services/MSS_StatusQueryPort";
        String msspReceiptUrl      = "https://dev-ae.mssp.dna.fi/soap/services/MSS_ReceiptPort";

        log.info("creating FiComClient");
        FiComClient fiComClient = new FiComClient(apId, 
                                                  apPwd, 
                                                  aeMsspIdUri, 
                                                  msspSignatureUrl, 
                                                  msspStatusUrl, 
                                                  msspReceiptUrl);
        
        String apTransId = "A"+System.currentTimeMillis();

        Service noSpamService = FiComAdditionalServices.createNoSpamService("A12", false);
        LinkedList<Service> additionalServices = new LinkedList<Service>();
        LinkedList<String> attributeNames = new LinkedList<String>();
        attributeNames.add(FiComAdditionalServices.PERSON_ID_VALIDUNTIL);
        Service personIdService = FiComAdditionalServices.createPersonIdService(attributeNames);
        additionalServices.add(personIdService);
        
        try {
        	log.info("calling signText");
        	req = 
	        	fiComClient.signText(apTransId, 
	        			textToBeSigned, 
	        			phoneNumber, 
	        			noSpamService, 
	        			additionalServices, 
	        			new FiComResponseHandler() {
			        		@Override
			        		public void onResponse(FiComRequest req, FiComResponse resp) {
			        			log.info("got resp");
			        			amountOfCalls--;
			        			
			        			try {
			        				responseBox.setText("MSS Signature: " + 
			        						new String(Base64.encode(resp.getMSS_StatusResp().
			        						getMSS_Signature().getBase64Signature()), "ASCII") +
			        						"\n\n" + responseBox.getText());
			        			} catch (UnsupportedEncodingException e) {
			        				log.info("Unsupported encoding", e);
			        			}
			        			
			        			for(PersonIdAttribute a : resp.getPersonIdAttributes()) {
			        				log.info(a.getName() + " " + a.getStringValue());
			        				responseBox.setText(a.getStringValue() + "\n" + responseBox.getText());
			        			}
			        			
			        			responseBox.setText(textToBeSigned + "\n" + responseBox.getText());
			        		}
			
			        		@Override
			        		public void onError(FiComRequest req, Throwable throwable) {
			        			amountOfCalls--;
			        			log.info("got error", throwable);
			        			responseBox.setText("ERROR, " + phoneNumber + "\n" +
			        					responseBox.getText());
			        		}
	        			});
        }
        catch (IOException e) {
            log.info("error establishing connection", e);
        }

        fiComClient.shutdown();
	}
	
	/**
	 * Asks a user for text to sign.
	 * @param args
	 */	
	public static void main(String[] args) {
		initComponents();
		callStateProgressBarUpdater.start();
	}
	
    private static void initComponents() {
    	frame = new javax.swing.JFrame("Sign Text");
    	frame.setResizable(false);
        pane = new javax.swing.JPanel();
        lblTxtToBeSigned = new javax.swing.JLabel();
        lblNumber = new javax.swing.JLabel();
        number = new javax.swing.JTextField();
        textToBeSigned = new javax.swing.JTextField();
        sendButton = new javax.swing.JButton();
        callStateProgressBar = new javax.swing.JProgressBar(0, 100);
        cancelButton = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        responseBox = new javax.swing.JTextArea();

        frame.setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        frame.setVisible(true);
        
        lblNumber.setText("Phone number");

        number.setText("+35847001001");

        lblTxtToBeSigned.setText("Text to be signed");

        textToBeSigned.setText("Sample text");

        sendButton.setText("Send");
        sendButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				amountOfCalls++;
				estamblishConnection(number.getText(), textToBeSigned.getText());
			}
		});
        
        cancelButton.setText("Cancel");
        cancelButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				req.cancel();
				amountOfCalls--;
			}
		});
        responseBox.setColumns(20);
        responseBox.setRows(5);
        jScrollPane1.setViewportView(responseBox);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(pane);
        pane.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
               .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 341, Short.MAX_VALUE)
                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addComponent(textToBeSigned, javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel1Layout.createSequentialGroup()
                            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                .addComponent(number, javax.swing.GroupLayout.DEFAULT_SIZE, 180, Short.MAX_VALUE)
                                .addComponent(lblNumber)
                                .addComponent(lblTxtToBeSigned)
                                .addGroup(jPanel1Layout.createSequentialGroup()
                                    .addComponent(sendButton)
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                    .addComponent(callStateProgressBar, 0, 0, Short.MAX_VALUE)))
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addComponent(cancelButton))))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(cancelButton))
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel1Layout.createSequentialGroup()
                        .addComponent(lblNumber)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(number, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(lblTxtToBeSigned)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(textToBeSigned, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(callStateProgressBar, javax.swing.GroupLayout.DEFAULT_SIZE, 23, Short.MAX_VALUE)
                            .addComponent(sendButton))))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 168, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(frame.getContentPane());
        frame.getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(pane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(pane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        frame.pack();
    }
    
    private static javax.swing.JFrame frame;
    private static javax.swing.JButton sendButton;
    private static javax.swing.JButton cancelButton;
    private static javax.swing.JLabel lblNumber;
    private static javax.swing.JLabel lblTxtToBeSigned;
    private static javax.swing.JPanel pane;
    protected static javax.swing.JProgressBar callStateProgressBar;
    private static javax.swing.JScrollPane jScrollPane1;
    private static javax.swing.JTextArea responseBox;
    private static javax.swing.JTextField number;
    private static javax.swing.JTextField textToBeSigned;
	
}

class SignTextProggerssBarUpdater extends Thread {
	
	public void run() {
		while (true) {
			if (SignText.amountOfCalls > 0) {
				int value = SignText.callStateProgressBar.getValue() > 90 ? 10 : SignText.callStateProgressBar.getValue()+10;
				SignText.callStateProgressBar.setValue(value);
			} else {
				SignText.callStateProgressBar.setValue(0);
			}
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
