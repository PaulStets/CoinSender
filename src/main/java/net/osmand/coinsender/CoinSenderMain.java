package net.osmand.coinsender;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.stream.JsonReader;
import net.osmand.coinsender.APIException;
import net.osmand.coinsender.wallet.Wallet;

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

    private static Map<String, Object> recipients;

    public static void main(String args[]) throws IOException {

        System.out.print("Enter your wallet ID: ");
        Scanner in = new Scanner(System.in);
        guid = in.nextLine();
        System.out.print("Enter your password: ");
        pass = in.nextLine();
        in.close();

        Wallet myWallet = new Wallet(SERVICE_URL, null, guid, pass);
        recipients = new HashMap<>();

        try {
            System.out.println("Balance: " + String.valueOf(myWallet.getBalance()));
        } catch (APIException | IOException e) {
            e.printStackTrace();
        }

        Gson gson = new Gson();
        JsonReader reader = new JsonReader(new FileReader(System.getProperty("user.home")
                + "/" + "report-2017-05.json"));
        recipients.putAll((Map) gson.fromJson(reader, Map.class));
        List<LinkedTreeMap> paymentsList = (ArrayList) recipients.get("payments");
        Map<String, Long> payments = new HashMap<>();
        for (LinkedTreeMap map : paymentsList) {
            Double sum = (Double) map.get("btc");
            Double satoshi = sum / 0.00000001;
            Long inSatoshi = convertToSatoshi(satoshi);
            if (payments.containsKey(map.get("btcaddress"))) {
                inSatoshi += payments.get(map.get("btcaddress"));
            }
            payments.put((String) map.get("btcaddress"), inSatoshi);
        }
        List<Map> splitPayment = splitResults(payments);
        for (Map<String, Long> part : splitPayment) {
            try {
                System.out.println(myWallet.sendMany(part, null, null, "OSM Live payment"));
            } catch (APIException e) {
                e.printStackTrace();
            }
        }

    }

    private static Long convertToSatoshi(Double sum) {
        return Math.round(sum);
    }


    private static List splitResults(Map<String, Long> map) {
        List<Map<String, Long>> res = new ArrayList<>();
        Map<String, Long> part = new HashMap<>();
        int count = 1;
        for (String key : map.keySet()) {
            part.put(key, map.get(key));
            if (part.size() == 50 || count == map.keySet().size()) {
                res.add(part);
                part = new HashMap<>();
            }
            count++;
        }
        return res;
    }
}
