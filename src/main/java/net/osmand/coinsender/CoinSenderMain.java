package net.osmand.coinsender;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.stream.JsonReader;
import net.osmand.coinsender.APIException;
import net.osmand.coinsender.wallet.PaymentResponse;
import net.osmand.coinsender.wallet.Wallet;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Created by Paul on 07.06.17.
 */
public class CoinSenderMain {

    private static final String SERVICE_URL = "http://127.0.0.1:3000/";

    private static String guid;
    private static String pass;
    private static String directory;

    private static final String FROM_ADDRESS = "1GRgEnKujorJJ9VBa76g8cp3sfoWtQqSs4";

    private static Map<String, Object> recipients;

    public static void main(String args[]) throws IOException {

        if (args.length <= 0) {
            System.out.println("Usage: --prepare | --send");
            return;
        }


        System.out.print("Enter your wallet ID: ");
        Scanner in = new Scanner(System.in);
        guid = in.nextLine();
        System.out.print("Enter your password: ");
        pass = in.nextLine();
        System.out.print("Enter full path to JSON file: ");
        directory = in.nextLine();


        Wallet myWallet = new Wallet(SERVICE_URL, null, guid, pass);
        recipients = new LinkedHashMap<>();

        try {
            System.out.println("Balance: " + String.valueOf(myWallet.getBalance()));
        } catch (APIException | IOException e) {
            e.printStackTrace();
        }

        Gson gson = new Gson();
        File file = new File(directory);
        if (!file.exists()) {
            while (!file.exists()) {
                System.out.print("You have entered incorrect file path. Please try again: ");
                directory = in.nextLine();
                file = new File(directory);
            }
        }
        JsonReader reader = new JsonReader(new FileReader(directory));
        recipients.putAll((Map) gson.fromJson(reader, Map.class));
        List<LinkedTreeMap> paymentsList = (ArrayList) recipients.get("payments");
        Map<String, Long> payments = new LinkedHashMap<>();
        long allMoney = 0;
        for (LinkedTreeMap map : paymentsList) {
            Double sum = (Double) map.get("btc");
            Double satoshi = sum / 0.00000001;
            Long inSatoshi = convertToSatoshi(satoshi);
            allMoney += inSatoshi;
            if (payments.containsKey(map.get("btcaddress"))) {
                inSatoshi += payments.get(map.get("btcaddress"));
            }

            payments.put((String) map.get("btcaddress"), inSatoshi);
        }
        List<Map> splitPayment = splitResults(payments);
        if (args.length > 0) {
            if (args[0].equals("--prepare")) {
                String totalSum = String.format("%.12f", (allMoney * 0.00000001));
                System.out.println("Number of chunks: " + splitPayment.size());
                System.out.println("Total sum in BTC: " + totalSum);
            }
            else if (args[0].equals("--send")) {
                boolean done = false;
                List<Integer> paidChunks = new ArrayList<>();
                while (!done) {
                    System.out.println("Number of chunks: " + splitPayment.size());
                    System.out.print("Enter the number of chunk you want to pay for ('-1' to exit): ");
                    int chunk = in.nextInt();
                    if (chunk == -1) {
                        break;
                    }
                    while (chunk < 0 || chunk >= splitPayment.size()) {
                        System.out.print("Please enter a number between 0 and " + (splitPayment.size() - 1) + ": ");
                        chunk = in.nextInt();
                    }
                    Map<String, Long> currentPayment = splitPayment.get(chunk);
                    System.out.println("All payments: ");
                    Long total = 0l;
                    for (String key : currentPayment.keySet()) {
                        total += currentPayment.get(key);
                        String currentPaymentString = String.format("%.12f", currentPayment.get(key) * 0.00000001);
                        System.out.println("Address: " + key + ", BTC: " + currentPaymentString);
                    }
                    Scanner scanner = new Scanner(System.in);
                    String totalString = String.format("%.12f", (total * 0.00000001));
                    System.out.print("Are you sure you want to pay " + totalString + " BTC? [y/n]: ");
                    String answer = scanner.nextLine();

                    if (!answer.toLowerCase().equals("y")) {
                        break;
                    }
                    System.out.println("Paying for chunk " + chunk + "...");
                    PaymentResponse paymentResponse = null;
                    try {
                        paymentResponse = myWallet.sendMany(currentPayment, FROM_ADDRESS, null, null);
                    } catch (APIException e) {
                        e.printStackTrace();
                    }
                    if (paymentResponse != null) {
                        paidChunks.add(chunk);
                        System.out.println(paymentResponse.getMessage());
                        System.out.println("Transaction Successful!");
                        System.out.println("You have paid for these chunks: " + paidChunks.toString());
                    }
                    else {
                        System.out.println("Transaction failed!");
                    }
                }
            }

        }


    }

    private static Long convertToSatoshi(Double sum) {
        return Math.round(sum);
    }


    private static List splitResults(Map<String, Long> map) {
        List<Map<String, Long>> res = new ArrayList<>();
        Map<String, Long> part = new LinkedHashMap<>();
        int count = 1;
        for (String key : map.keySet()) {
            part.put(key, map.get(key));
            if (part.size() == 50 || count == map.keySet().size()) {
                res.add(part);
                part = new LinkedHashMap<>();
            }
            count++;
        }
        return res;
    }
}
