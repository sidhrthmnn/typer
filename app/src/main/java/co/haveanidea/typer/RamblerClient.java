package co.haveanidea.typer;

import org.json.JSONObject;
import javax.net.ssl.HttpsURLConnection;
import java.net.URL;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Provider keys stay on the server. No editor context is accepted by this API. */
final class RamblerClient {
    static String rewrite(String endpoint,String token,String draft,String instruction) throws Exception {
        if(draft.length()>8000||instruction.length()>1000)throw new IOException("Draft too long (maximum 8,000 characters).");
        URL url=new URL(endpoint);
        if(!url.getProtocol().equals("https")||url.getUserInfo()!=null)throw new IOException("Configure an HTTPS server in Typer settings.");
        HttpsURLConnection c=(HttpsURLConnection)url.openConnection();
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(30000);
        c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
        c.setRequestProperty("Authorization","Bearer "+token);
        JSONObject body=new JSONObject().put("draft",draft).put("instruction",instruction);
        try {
            try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}
            if(c.getResponseCode()!=200)throw new IOException("AI server returned "+c.getResponseCode()+". Your draft is unchanged.");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=c.getInputStream()) { byte[] buffer=new byte[2048];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>65536)throw new IOException("AI response too large.");bytes.write(buffer,0,n);} }
            String result=new JSONObject(bytes.toString("UTF-8")).getString("text").trim();
            if(result.isEmpty()||result.length()>16000)throw new IOException("AI returned an invalid draft.");
            return result;
        } finally { c.disconnect(); }
    }
}
