package bank.kyc;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Πελάτης για την υπηρεσία «Μητρώο Βασικών Στοιχείων Επιχειρήσεων» RgWsPublic2 της ΑΑΔΕ (SOAP 1.2 + WS-Security UsernameToken).
 *
 * ΠΡΟΣΟΧΗ: Γράφτηκε χωρίς πρόσβαση στην πραγματική υπηρεσία (απαιτούνται ειδικοί κωδικοί πρόσβασης) και ΔΕΝ έχει
 * δοκιμαστεί με ζωντανή απάντηση. Τα σημεία που πρέπει να επιβεβαιωθούν με το WSDL
 * (https://www1.gsis.gr/wsaade/RgWsPublic2/RgWsPublic2?wsdl) είναι τα σταθερά ΜΕΡΟΣ 1 και οι κωδικοί σφάλματος στο parse().
 * Η ανάγνωση της απάντησης γίνεται με βάση το τοπικό όνομα των στοιχείων (χωρίς namespace), ώστε να αντέχει σε αλλαγές prefix.
 */
public class AadeRegistryClient implements AadeService {

    // ===================== ΜΕΡΟΣ 1: ΕΠΙΒΕΒΑΙΩΣΕ ΜΕ ΤΟ WSDL =====================
    static final String DEFAULT_ENDPOINT = "https://www1.gsis.gr/wsaade/RgWsPublic2/RgWsPublic2";
    static final String NS_SERVICE = "http://rgwspublic2/RgWsPublic2Service";
    static final String NS_TYPES = "http://rgwspublic2/RgWsPublic2";
    static final String NS_WSSE = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";

    private final String username;
    private final String password;
    private final String calledBy;
    private final String endpoint;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public AadeRegistryClient(String username, String password, String calledBy) {
        this(username, password, calledBy, DEFAULT_ENDPOINT);
    }

    public AadeRegistryClient(String username, String password, String calledBy, String endpoint) {
        this.username = username;
        this.password = password;
        this.calledBy = calledBy;
        this.endpoint = endpoint;
    }

    @Override
    public Optional<AadeRecord> lookup(String afm) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/soap+xml; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(buildRequest(afm), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int code = resp.statusCode();
            if (code == 401 || code == 403)
                throw new AadeException("Η ΑΑΔΕ απέρριψε τα διαπιστευτήρια (HTTP " + code + ").");
            if (code == 429)
                throw new AadeException("Υπέρβαση ορίου κλήσεων της ΑΑΔΕ. Δοκιμάστε ξανά σε λίγο.");
            // Σε SOAP σφάλματα η υπηρεσία συχνά απαντά HTTP 500 με Fault· το parse() το χειρίζεται.
            if (code / 100 != 2 && code != 500)
                throw new AadeException("Σφάλμα από την ΑΑΔΕ (HTTP " + code + ").");
            return parse(resp.body(), afm);
        } catch (IOException e) {
            throw new AadeException("Αδυναμία σύνδεσης με την ΑΑΔΕ: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AadeException("Η αναζήτηση διακόπηκε.", e);
        }
    }

    String buildRequest(String afm) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<env:Envelope xmlns:env=\"http://www.w3.org/2003/05/soap-envelope\""
                + " xmlns:ns1=\"" + NS_WSSE + "\" xmlns:ns2=\"" + NS_SERVICE + "\" xmlns:ns3=\"" + NS_TYPES + "\">"
                + "<env:Header><ns1:Security><ns1:UsernameToken>"
                + "<ns1:Username>" + xml(username) + "</ns1:Username>"
                + "<ns1:Password>" + xml(password) + "</ns1:Password>"
                + "</ns1:UsernameToken></ns1:Security></env:Header>"
                + "<env:Body><ns2:rgWsPublic2AfmMethod><ns2:INPUT_REC>"
                + "<ns3:afm_called_by>" + xml(calledBy) + "</ns3:afm_called_by>"
                + "<ns3:afm_called_for>" + xml(afm) + "</ns3:afm_called_for>"
                + "</ns2:INPUT_REC></ns2:rgWsPublic2AfmMethod></env:Body></env:Envelope>";
    }

    /** Μετατρέπει την απάντηση SOAP σε AadeRecord (package-private ώστε να δοκιμάζεται χωρίς δίκτυο). */
    static Optional<AadeRecord> parse(String body, String requestedAfm) {
        Document doc;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var builder = f.newDocumentBuilder();
            builder.setErrorHandler(null);   // να μην τυπώνει σφάλματα parsing στην κονσόλα
            doc = builder.parse(new InputSource(new StringReader(body)));
        } catch (Exception e) {
            throw new AadeException("Μη αναγνώσιμη απάντηση από την ΑΑΔΕ.", e);
        }
        Element root = doc.getDocumentElement();

        Element fault = first(root, "Fault");
        if (fault != null) {
            String reason = text(fault, "Text");
            if (reason == null) reason = text(fault, "faultstring");
            throw new AadeException("Σφάλμα SOAP από την ΑΑΔΕ" + (reason == null ? "." : ": " + reason));
        }

        Element err = first(root, "error_rec");
        String errCode = err == null ? null : text(err, "error_code");
        if (errCode != null) {
            String descr = text(err, "error_descr");
            // ΕΠΙΒΕΒΑΙΩΣΕ τους ακριβείς κωδικούς: εδώ θεωρείται «δεν βρέθηκε» ό,τι αφορά το ΑΦΜ που ψάχνουμε.
            String c = errCode.toUpperCase();
            if (c.contains("CALLED_FOR") || c.contains("NOT_FOUND") || c.contains("WRONG_AFM") || c.contains("INVALID_AFM"))
                return Optional.empty();
            throw new AadeException("Σφάλμα ΑΑΔΕ " + errCode + (descr == null ? "" : ": " + descr));
        }

        Element basic = first(root, "basic_rec");
        if (basic == null || text(basic, "afm") == null) return Optional.empty();

        String afm = text(basic, "afm");
        String name = nz(text(basic, "onomasia"));
        String flagDescr = nz(text(basic, "i_ni_flag_descr"));
        String norm = norm(flagDescr);
        boolean natural = norm.contains("φυσικο") && !norm.contains("μη");

        List<AadeRecord.Activity> activities = new ArrayList<>();
        Element tab = first(root, "firm_act_tab");
        if (tab != null) {
            NodeList items = tab.getElementsByTagNameNS("*", "item");
            for (int i = 0; i < items.getLength(); i++) {
                Element it = (Element) items.item(i);
                String code = text(it, "firm_act_code");
                if (code == null) continue;
                activities.add(new AadeRecord.Activity(code, nz(text(it, "firm_act_descr")),
                        "1".equals(text(it, "firm_act_kind"))));
            }
        }

        return Optional.of(new AadeRecord(
                afm != null ? afm : requestedAfm, name, nz(text(basic, "commer_title")),
                nz(text(basic, "legal_status_descr")), natural,
                nz(text(basic, "deactivation_flag")), nz(text(basic, "deactivation_flag_descr")),
                nz(text(basic, "firm_flag_descr")),
                nz(text(basic, "doy_descr")), address(basic),
                date(text(basic, "regist_date")), date(text(basic, "stop_date")), activities));
    }

    private static String address(Element basic) {
        String street = nz(text(basic, "postal_address"));
        String no = nz(text(basic, "postal_address_no"));
        String zip = nz(text(basic, "postal_zip_code"));
        String area = nz(text(basic, "postal_area_description"));
        StringBuilder sb = new StringBuilder(street);
        if (!no.isEmpty()) sb.append(sb.length() > 0 ? " " : "").append(no);
        if (!zip.isEmpty()) sb.append(sb.length() > 0 ? ", " : "").append(zip);
        if (!area.isEmpty()) sb.append(sb.length() > 0 ? " " : "").append(area);
        return sb.toString();
    }

    // ---------------------------------------------------------------- βοηθητικά XML

    private static Element first(Element parent, String localName) {
        NodeList l = parent.getElementsByTagNameNS("*", localName);
        return l.getLength() == 0 ? null : (Element) l.item(0);
    }

    /** Κείμενο του πρώτου στοιχείου με αυτό το τοπικό όνομα, ή null αν λείπει/είναι κενό (xsi:nil). */
    private static String text(Element parent, String localName) {
        Element e = first(parent, localName);
        if (e == null) return null;
        String t = e.getTextContent();
        return (t == null || t.isBlank()) ? null : t.trim();
    }

    private static LocalDate date(String s) {
        if (s == null || s.length() < 10) return null;
        try {
            return LocalDate.parse(s.substring(0, 10));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String norm(String s) {
        return java.text.Normalizer.normalize(s.toLowerCase(), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    @Override
    public String sourceName() {
        return "ΑΑΔΕ RgWsPublic2";
    }

    @Override
    public boolean isLive() {
        return true;
    }
}
