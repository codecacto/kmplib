// Gera core/src/commonMain/kotlin/br/com/codecacto/kmplib/validation/PhoneRegionData.kt
// a partir dos metadados do libphonenumber (Google, Apache License 2.0).
//
//   curl -sSfLO https://repo1.maven.org/maven2/com/googlecode/libphonenumber/libphonenumber/<v>/libphonenumber-<v>.jar
//   javac -cp libphonenumber-<v>.jar GeneratePhoneRegionData.java
//   java -cp libphonenumber-<v>.jar:. GeneratePhoneRegionData > /tmp/regions.tsv
//
// Saída: TSV `região \t DDI \t min \t max \t prefixo-de-tronco`, uma linha por região suportada.
// O .kt é montado a partir desse TSV (ver o cabeçalho do arquivo gerado). Última geração: 8.13.52.
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonemetadata.PhoneMetadata;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

public class GeneratePhoneRegionData {
    public static void main(String[] args) throws Exception {
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        // `getMetadataForRegion` não é público; é a única porta para o `possibleLength` por região.
        Method metadata = PhoneNumberUtil.class.getDeclaredMethod("getMetadataForRegion", String.class);
        metadata.setAccessible(true);
        for (String region : new TreeSet<>(util.getSupportedRegions())) {
            PhoneMetadata m = (PhoneMetadata) metadata.invoke(util, region);
            List<Integer> lengths = m.getGeneralDesc().getPossibleLengthList();
            System.out.println(region + "\t" + m.getCountryCode() + "\t" + Collections.min(lengths)
                + "\t" + Collections.max(lengths) + "\t" + m.getNationalPrefix());
        }
    }
}
