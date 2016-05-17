package com.main.helpers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.main.comments.CommentsHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;

public class ALMHelper {
    private String ALM_AUTH_URL;
    private String ALM_CREDENTIALS;
    private String cookies = "";
    private String[] excludedUsers;
    private Properties config = new Properties();
    private static final Logger log = LogManager.getLogger(ALMHelper.class.getName());
    private static final Logger logger = LogManager.getLogger("Defects");

    public ALMHelper() {
        setConfig();
        excludedUsers = getListOfExcludedUsers();
    }

    public void setListOfExcludedUsers(String[] arr) {
        excludedUsers = arr;
    }

    public Properties getConfig() {
        return config;
    }

    /*
    Инициализация конфига
    Считываем все настройки из конфигурационного файла.
    Вызывается в конструкторе ALMHelper'а.
    Надо бы потом вынести конфиг, чтобы он был глобальным.
     */
    public void setConfig() {

        try {
            config.load(this.getClass().getResourceAsStream("/Settings/config.properties"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            log.error(e.toString());
        } catch (IOException e) {
            e.printStackTrace();
            log.error(e.toString());
        }
    }

    /*
    Метод для аутентификации
    В текущей реализации данные для аутентификации берутся из конфигурационного файла.
    Данные задаются в следующем виде: login:password
    В дальнейшем они преобразуются в BASE64 и отправляются на сервер.
    Полученные в ответе cookies сохраняем, чтобы передавать при дальнейших запросах.
     */

    public void POSTAuthenticate() {
        log.info("Starting authorization!");
        Map<String, List<String>> map;
        String authData = config.getProperty("alm.server.api.user") + ":" + config.getProperty("alm.server.api.password");
        ALM_CREDENTIALS = "Basic " + Base64.getEncoder().encodeToString(authData.getBytes());
        ALM_AUTH_URL = config.getProperty("alm.server.api.url") + "authentication/sign-in";
        try {

            URL url = new URL(ALM_AUTH_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", ALM_CREDENTIALS);
            conn.connect();
            if (conn.getResponseCode() != 200) {
                conn.disconnect();
                throw new RuntimeException("Failed: HTTP error code: Unable to sign in. Please check your credentials!");
            } else {
                map = conn.getHeaderFields();
                conn.disconnect();
                boolean k = false;
                /*
                Сейчас в куки пихается слишком много пустых Path=. Надо будет сделать какую-нибудь чистку
                Дело в том, что ALM возвращает несколько Header'ов Set-Cookie, и в каждом из них есть такой параметр.
                На работоспособности не сказывается, но забивает куки лишним мусором
                                 */
                for (int i = 0; i < map.size(); i++) {
                    if (map.containsKey("Set-Cookie")) {
                        if (!k) {
                            this.cookies = cookies + map.get("Set-Cookie");
                            k = true;
                        } else this.cookies = cookies + ";" + map.get("Set-Cookie");
                    }
                }
                this.cookies = cookies.replace(",", ";");

            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
            log.error(e.toString());
        } catch (IOException e) {
            e.printStackTrace();
            log.error(e.toString());
        }
        log.info("Successfuly authorized!");
    }

    /*
    Метод для закрытия пользовательской сессии в ALM. В противном случае сессия будет висеть несколько часов.
     */
    public void signOut() {
        try {
            URL url = new URL(config.getProperty("alm.server.api.url") + "authentication/sign-out");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", ALM_CREDENTIALS);
            if (conn.getResponseCode() != 200) {
                conn.disconnect();
                throw new RuntimeException("Failed: Unable to log out from ALM! Please check your credentials");
            } else {
                this.cookies = null;
                log.info("Signed out. Good bye!");
            }
        } catch (MalformedURLException e) {
            log.error(e.toString());
        } catch (IOException e) {
            log.error(e.toString());
        }
    }

    /*
    Метод для получения информации о дефекте
    В текущей реализации преобразует тело ответа (Response) в строку и возвращает.
    В дальнейшем парсится в JSON-объект с помощью GSON.
    Нас интересуют поля:
    id - ID дефекта
    owner - Пользователь, на которого возвращаем дефект
    status - Статус дефекта
    dev-comments - Комментарий к дефекту
 */

    public JsonObject GETDefect(int id) {
        log.info("Fetching defect #" + id + " from ALM");
        JsonObject obj = new JsonObject();
        String message;

        try {
            log.debug(getDefectURL(id));
            URL url = getDefectURL(id);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Cookie", cookies);
            conn.connect();
            if (conn.getResponseCode() != 200) {
                conn.disconnect();
                throw new RuntimeException("GET failed: HTTP error code: " + conn.getResponseCode());
            }
            BufferedInputStream is = new BufferedInputStream(conn.getInputStream());
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            byte bout[] = getByteArray(is);
            JsonParser parser = new JsonParser();
            message = new String(bout, StandardCharsets.UTF_8);
            obj = parser.parse(message).getAsJsonObject();
            conn.disconnect();
        } catch (MalformedURLException e) {
            log.error(e.toString());
        } catch (IOException e) {
            log.error(e.toString());
        }
        log.debug(obj.get("dev-comments"));
        return obj;
    }


    /*
    Метод, позволяющий получить JSON-объект с готовыми к отправке в ALM изменениями.
    */
    public JsonObject prepareDefect(JsonObject obj) throws IOException, RuntimeException {
        log.info("Preparing Defect #" + obj.get("id").toString() + " for update");
        JsonObject object = new JsonObject();
        Calendar cal = Calendar.getInstance();
        String devComment;
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.YYYY");
        if (obj.get("dev-comments").toString().equals("null")) {
            devComment = CommentsHelper.createComment(config.getProperty("alm.defect.comment"), config.getProperty("alm.server.api.credentials"), config.getProperty("alm.server.api.user"));
        } else {
            //String sourceComment = obj.get("dev-comments").toString().replace("</body></html>", "").replace("\\\"", "\"").replace("\\n", "");
            //devComment = sourceComment.substring(1, sourceComment.length() - 1).concat(CommentsHelper.createComment(config.getProperty("alm.defect.comment"), config.getProperty("alm.server.api.credentials"), config.getProperty("alm.server.api.user")));
            String source = obj.get("dev-comments").toString();
            devComment = CommentsHelper.addComment(source,config.getProperty("alm.defect.comment"), config.getProperty("alm.server.api.credentials"), config.getProperty("alm.server.api.user"));
        }
        //Если дефект в статусе Closed, то не трогаем его
        if (obj.get("status").toString().replace("\"", "").equals("Closed")) {
            logger.error("Defect #" + obj.get("id").toString() + " is already Closed");
            throw new RuntimeException("Defect #" + obj.get("id").toString() + " is already Closed");
        } else if (obj.get("status").toString().replace("\"", "").equals("Fixed")) {
            logger.error("Defect #" + obj.get("id").toString() + " is already Fixed");
            throw new RuntimeException("Defect #" + obj.get("id").toString() + " is already Fixed");
        } else {
            {
            /*
            В этом блоке мы проверяем, что автора дефекта нет в списке пользователей, перевод дефектов на которых запрещен.
            Если есть, то возвращаем на пользователя по умолчанию. Если нет, то возвращаем на автора
             */
                String owner;
                for (int i = 0; i < excludedUsers.length; i++) {
                    if (obj.get("detected-by").toString().replace("\"", "").equals(excludedUsers[i])) {
                        object.addProperty("owner", config.getProperty("alm.default.user"));
                        break;
                    } else {
                        owner = obj.get("detected-by").toString();
                        owner = owner.substring(1, owner.length() - 1);
                        object.addProperty("owner", owner);
                    }
                }
                log.debug(devComment + "\n--------------------------------------------------\n");
                object.addProperty("dev-comments", devComment);
                object.addProperty("status", "Fixed");
                object.addProperty("id", obj.get("id").toString());
                log.info("Defect has been prepared!");
                return object;
            }
        }
    }


    /*
     Метод для обновления информации по дефекту в ALM.
     На вход подается JSON-объект, из которого мы берем лишь нужные нам поля и отправляем PUT HTTP-запрос.
      */
    public void PUTUpdateDefect(JsonObject obj) throws IOException {
        log.debug(obj);
        log.info("Updating defect #" + obj.get("id").toString());
        HttpURLConnection conn = (HttpURLConnection) getDefectURL(obj.get("id").getAsInt()).openConnection();
        conn.setRequestMethod("PUT");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Cookie", cookies);
        conn.setDoOutput(true);
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.YYYY");
        byte[] output = obj.toString().getBytes(StandardCharsets.UTF_8);
        OutputStream os = conn.getOutputStream();
        os.write(output);
        conn.connect();
        os.close();
        if (conn.getResponseCode() != 200) {
            conn.disconnect();
            throw new RuntimeException("Update failed: HTTP error code: " + conn.getResponseCode());
        }
        log.info("Success! Defect " + obj.get("id") + " has been updated");
    }

    /*
 Метод для получения списка дефектов из файла.
 В текущей реализации файл формируется вручную. В дальнейшем приложение будет крутиться постоянно, ожидая нового списка.
 */
    public List<Integer> getList() throws IOException {
        log.info("Receiving list of fixed defects");
        List<Integer> array = new ArrayList<Integer>();
        Path path = Paths.get(config.getProperty("alm.defects.list.file.path") + "list.txt");
        String i;
        if (Files.exists(path)) {
            File file = new File(path.toString());
            BufferedReader reader = new BufferedReader(new FileReader(file));
            while ((i = reader.readLine()) != null) {
                array.add(Integer.parseInt(i));
            }
            reader.close();
            /*
            Проверка на дубликаты дефектов в списке
             */
            for (int j = 0; j < array.size(); j++) {
                for (int k = 0; k < array.size(); k++) {
                    if (j != k) {
                        if (array.get(j) == array.get(k)) array.remove(j);
                    }
                }
            }
            log.info("List of defects has been received!");
        } else throw new FileNotFoundException("The list file doesn't exist or have an inappropriate name!");
        return array;
    }

    /*
    Вспомогательный метод для получения тела Response-сообщения. Зачем в метод запихнул - а хрен его знает.
     */
    public byte getByteArray(InputStream is)[]throws IOException {
        int nRead;
        byte[] data = new byte[16384];
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            bout.write(data, 0, nRead);
        }
        try {
            bout.flush();
        } catch (IOException e) {
            log.error(e.toString());
        }
        return bout.toByteArray();
    }

    /*
        Метод для получения списка пользователей, на которых нельзя возвращать дефекты
         */
    public String[] getListOfExcludedUsers() {
        log.info("Receiving list of excluded users");
        String start = config.getProperty("alm.exclude.users");
        String[] excludeUsers = start.split(",");
        log.info("List has been received!");
        return excludeUsers;
    }

    public void getVersion() {
        log.info("Current version: " + config.getProperty("alm.helper.version"));
        log.info("Build date: " + config.getProperty("alm.helper.build.date"));
    }

    /*
    Метод для архивирования списка дефектов текущей поставки.
    Метод копирует файл в заданную директорию и меняет имя файла на текущую дату.
    Т.о. мы знаем составы всех поставок по датам.
     */
    public void archiveList() throws IOException {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.YYYY");
        Path sourcePath = Paths.get(config.getProperty("alm.defects.list.file.path") + "list.txt");
        Path destinationPath = Paths.get(config.getProperty("alm.helper.history.directory") + sdf.format(cal.getTime()) + ".txt");
        Files.copy(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
        Files.delete(sourcePath);
    }
    /*
    Метод для получения ссылки на дефект для отправки HTTP-запросов в ALM
     */
    public URL getDefectURL(int id) throws MalformedURLException {
        URL url = new URL(config.getProperty("alm.server.api.url") + "domains/" + config.getProperty("alm.server.api.domain")
                + "/" + "projects/" + config.getProperty("alm.server.api.project") + "/defects/" + id);
        log.debug(url);
        return url;
    }

    public static void process(ALMHelper helper) {
        JsonObject defect;
        List<Integer> array = new ArrayList<Integer>();

        log.info("--------------------------------------------------------");
        /*
        Получаем список дефектов из файла
         */
        try {
            array = helper.getList();
        } catch (FileNotFoundException e) {
            log.error(e.toString());
        } catch (IOException e) {
            log.error(e.toString());
        }
        /*
        Отправляем запрос на аутентификацию в ALM
         */
        helper.POSTAuthenticate();
        JsonObject ob;
        /*
        Последовательно получаем дефекты, обрабатываем их и апдейтим в ALM
         */
        Iterator<Integer> iterator = array.iterator();
        while (iterator.hasNext()) {
            try {
                defect = helper.GETDefect(iterator.next());
                ob = helper.prepareDefect(defect);
                helper.PUTUpdateDefect(ob);
            } catch (RuntimeException e) {
                log.error(e.toString());
                log.error("Fetching next defect. Previous defect hasn't been updated");
                continue;
            } catch (IOException e) {
                log.error(e.toString());
                continue;
            }
        }
        helper.signOut();
        try {
            /*
            Архивируем список дефектов текущей поставки
             */
            helper.archiveList();
        } catch (IOException e) {
            log.error(e.toString());
        }
    }

    public static void processBadDefect(){}

    /*
Метод для создания дефекта в тестовом ALM
 */
    public void createDefect(){

    }
}
