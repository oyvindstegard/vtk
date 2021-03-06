/* Copyright (c) 2005, 2008 University of Oslo, Norway
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 
 *  * Neither the name of the University of Oslo nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *      
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package vtk.util.mail;

import javax.mail.Address;
import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.util.Arrays;
import java.util.List;

public final class MailExecutor {
    private static final Logger logger = LoggerFactory.getLogger(MailExecutor.class);

    private final JavaMailSender mailSender;
    private final TaskExecutor taskExecutor;
    private final String[] alwaysAcceptDomains;

    public MailExecutor(
            TaskExecutor taskExecutor,
            JavaMailSender mailSender,
            String[] alwaysAcceptDomains
    ) {
        this.taskExecutor = taskExecutor;
        this.mailSender = mailSender;
        this.alwaysAcceptDomains = alwaysAcceptDomains;
    }

    public void enqueue(MimeMessage msg) throws MessagingException {
        this.enqueue(msg, false);
    }
    
    public void enqueue(MimeMessage msg, boolean captchaValidated) throws MessagingException {
        if (!captchaValidated) {
            for (Address address : msg.getAllRecipients()) {
                if (!alwaysAccept(address.toString())) {
                    throw new AuthenticationFailedException(
                            String.format("Not authorized to send e-mail to %s", address)
                    );
                }
            }
        }
        taskExecutor.execute(new SendMailTask(this.mailSender, msg));
    }
    
    public MimeMessage createMimeMessage(String mailBody,
            String[] mailMultipleTo, String emailFrom,
            boolean sendCopyToSender, String subject) throws Exception {

        MimeMessage mimeMessage = this.mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage);

        helper.setSubject(subject);
        helper.setFrom(emailFrom);
        helper.setTo(mailMultipleTo);
        if (sendCopyToSender) {
            helper.setCc(emailFrom);
        }

        helper.setText(mailBody, true); // send HTML

        return mimeMessage;
    }
    
    public static boolean isValidEmail(String addr) {
        if (addr == null || addr.trim().equals("")) {
            return false;
        }
        if (org.springframework.util.StringUtils.countOccurrencesOf(addr, "@") == 0) {
            return false;
        }
        try {
            new InternetAddress(addr);
        } catch (AddressException e) {
            return false;
        }

        return true;
    }

    protected boolean alwaysAccept(String recipient) {
        for (String domain : alwaysAcceptDomains) {
            String lcRecipient = recipient.toLowerCase();
            int lastPartIndex = lcRecipient.length() - domain.length();
            if (lastPartIndex <= 0) continue;
            String domainPart = lcRecipient.substring(lastPartIndex);
            char prevChar = lcRecipient.charAt(lastPartIndex - 1);
            if (domainPart.equals(domain) && (prevChar == '@' || prevChar == '.')) {
                return true;
            }
        }
        return false;
    }

    private static class SendMailTask implements Runnable {

        private MimeMessage msg;
        private JavaMailSender sender;

        public SendMailTask(JavaMailSender javaMailSender, MimeMessage msg) {
            this.msg = msg;
            this.sender = javaMailSender;
        }

        public void run() {
            try {
                this.sender.send(msg);
                if (logger.isDebugEnabled()) {
                    logger.info("Sent message " + this.msg);
                }
            } catch (Throwable t) {
                logger.warn("Sending message " + this.msg + " failed", t);
            }
        }
    }
}
