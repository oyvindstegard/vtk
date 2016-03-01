package vtk.util.mail;

import org.assertj.core.util.Arrays;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MailExecutorTest {
    private static final String[] alwaysAccept = Arrays.array(
            "samordnaopptak.no",
            "norgeshistorie.no",
            "musikkarven.no",
            "cristin.no",
            "hlsenteret.no",
            "uio.no",
            "nordlys.info"
    );
    private final MailExecutor mailExecutor = new MailExecutor(null, null, alwaysAccept);


    @Test
    public void email_must_contain_one_and_only_one_at_sign() throws Exception {
        assertThat(MailExecutor.isValidEmail("valid@valid.no")).isTrue();
        assertThat(MailExecutor.isValidEmail("invlid@valid@valid.no")).isFalse();
        assertThat(MailExecutor.isValidEmail("invalid.no")).isFalse();
        assertThat(MailExecutor.isValidEmail("invalid")).isFalse();
    }

    @Test
    public void recipient_must_be_in_always_accept() throws Exception {
        assertThat(mailExecutor.alwaysAccept("valid@uio.no")).isTrue();
        assertThat(mailExecutor.alwaysAccept("valid@usit.uio.no")).isTrue();
        assertThat(mailExecutor.alwaysAccept("valid@nordlys.info")).isTrue();
        assertThat(mailExecutor.alwaysAccept("invalid@invalid.no")).isFalse();
    }

    @Test
    public void must_not_accept_recipient_with_accepted_domain_as_a_substring() throws Exception {
        assertThat(mailExecutor.alwaysAccept("invalid@nouio.no")).isFalse();
        assertThat(mailExecutor.alwaysAccept("invalid.uio.no@invalid.no")).isFalse();
        assertThat(mailExecutor.alwaysAccept("invalid@uio.no.invalid.no")).isFalse();
    }
}
