package com.siweifu.utils;


import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.mail.Address;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;

import org.apache.commons.lang3.ArrayUtils;

import com.jfinal.kit.StrKit;
import com.jfinal.log.Logger;

/**
 *  邮件工具类，处理自定义邮件，附件发送等
 * @title EmailUtils.java
 * @description 
 * @company 北京思维夫网络科技有限公司
 * @author 卢春梦  
 * @version 1.0
 * @created 2015年4月22日下午6:12:42
 */
public class EmailUtils {

	private static final Logger logger = Logger.getLogger(EmailUtils.class);

	/* 邮箱配置资源 */
	private static final ResourceBundle bundle = ResourceBundle.getBundle("mail");

	/* 邮箱配置详情 */
	private static final String MAIL_SMTP_AUTH 			= bundle.getString("mail.smtp.auth");
	private static final String MAIL_HOST 				= bundle.getString("mail.host");
	private static final String MAIL_TRANSPORT_PROTOCOL = bundle.getString("mail.transport.protocol");
	private static final String MAIL_SMTP_PORT 			= bundle.getString("mail.smtp.port");
	private static final String MAIL_AUTH_NAME 			= bundle.getString("mail.auth.name");
	private static final String MAIL_AUTH_PASSWORD 		= bundle.getString("mail.auth.password");
	private static final String MAIL_DISPLAY_SENDNAME 	= bundle.getString("mail.display.sendname");
	private static final String MAIL_DISPLAY_SENDMAIL 	= bundle.getString("mail.display.sendmail");
	private static final String MAIL_SEND_CHARSET 		= bundle.getString("mail.send.charset");
	private static final boolean MAIL_IS_DEBUG 			= Boolean.parseBoolean(bundle.getString("mail.is.debug"));

	/* 函数共用字段 */
	private static final Message message = initMessage();

	// 初始化邮箱配置
	private static final Message initMessage() {
		// 基本配置
		Properties props = new Properties();
		props.setProperty("mail.smtp.auth", 			MAIL_SMTP_AUTH);
		props.setProperty("mail.host", 					MAIL_HOST);
		props.setProperty("mail.transport.protocol", 	MAIL_TRANSPORT_PROTOCOL);
		props.setProperty("mail.smtp.port", 			MAIL_SMTP_PORT);
		// error:javax.mail.MessagingException: 501 Syntax: HELO hostname
		props.setProperty("mail.smtp.localhost", 		"127.0.0.1");

		Session session = Session.getInstance(props, new Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(MAIL_AUTH_NAME, MAIL_AUTH_PASSWORD);
			}
		});

		// debug模式
		session.setDebug(MAIL_IS_DEBUG);
		Message message = new MimeMessage(session);

		try {
			message.setFrom(new InternetAddress(MimeUtility.encodeText(MAIL_DISPLAY_SENDNAME)+ '<' + MAIL_DISPLAY_SENDMAIL + '>'));
		} catch (AddressException e) {
			logger.error(e.getMessage(), e);
			throw new RuntimeException(e.getMessage());
		} catch (UnsupportedEncodingException e) {
			logger.error(e.getMessage(), e);
			throw new RuntimeException(e.getMessage());
		} catch (MessagingException e) {
			logger.error(e.getMessage(), e);
			throw new RuntimeException(e.getMessage());
		}
		return message;
	}

	/**
	 * 发送邮件
	 * @param data
	 * @return 
	 * @throws MessagingException 
	 * @throws IOException 
	 */
	private static void send(MailSender data) throws MessagingException, IOException {
		// 主题
		message.setSubject(data.subject);
		// 对于不含文件
		if (StrKit.isBlank(data.attachfile)) {
			message.setContent( data.content, "text/html;charset=" + MAIL_SEND_CHARSET );
		} else {
			Multipart part = new MimeMultipart();
			// 文本的内容
			MimeBodyPart txtPart  = new MimeBodyPart(); 
			txtPart.setContent( data.content, "text/html;charset=" + MAIL_SEND_CHARSET );
			part.addBodyPart(txtPart);
			// 附件
			MimeBodyPart filePart = new MimeBodyPart();  
			filePart.attachFile( data.attachfile );
			part.addBodyPart(filePart);
			message.setContent(part);
		}
		// 发送的用户
		message.setRecipients(Message.RecipientType.TO, toAddress(data.to));
		// 抄送的用户
		message.setRecipients(Message.RecipientType.CC, toAddress(data.cc));
		Transport.send(message);
	}

	// 解决多线程问题，由于共享的message
	private static Lock lock = new ReentrantLock();

	/**
	 * 邮件发送
	 * @param data
	 * @return
	 */
	private static boolean sendMail(MailSender data) {
		lock.lock();
		try {
			send(data);
			return true;
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		} finally {
			lock.unlock();
		}
		return false;
	}

	/**
	 * 异步发送mail，一个新线程大概占用1M内存，请勿循环发送邮件，循环邮件请采用队列的方式
	 * @param data
	 */
	private static void asynMail(final MailSender data) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					EmailUtils.sendMail(data);
				} catch (Exception e) {
					Thread.currentThread().interrupt();
				}
			}
		}).start();
	}

	/**
	 * 将邮件地址转换
	 * @param emails
	 * @return
	 */
	private static Address[] toAddress(String... emails) {
		if (ArrayUtils.isEmpty(emails)) {
			return null;
		}
		// 过滤非正常的email
		Set<Address> addSet = new HashSet<Address>();
		for (String email : emails) {
			boolean result = RegexUtils.match(RegexUtils.EMAIL, email);
			if (!result) {
				continue;
			}
			try {
				addSet.add(new InternetAddress(email));
			} catch (AddressException e) {
				continue;
			}
		}
		return addSet.toArray(new Address[0]);
	}

	/**
	 * @description 
	 * 功能描述: 内部类，用于邮件数据的封装
	 * @author 		  作         者:  卢春梦
	 * @createdate   建立日期：2015-1-4上午10:33:05
	 * @projectname  项目名称: ylphone-web
	 */
	public static class MailSender {
		// 发送给
		private String[] to 		= null;
		// 抄送
		private String[] cc 		= null;
		// 主题
		private String subject		= null;
		// 内容
		private String content		= null;
		// 文件，预设一个附件，貌似暂时用不到多附件，多附件稍微改改就好，哈哈哈...
		private String attachfile	= null;
		// 默认同步
		private boolean asyn = false;

		public static MailSender New(){
			return new MailSender();
		}

		public MailSender to(String... toEmails) {
			this.to = toEmails;
			return this;
		}
		public MailSender cc(String... ccEmails) {
			this.cc = ccEmails;
			return this;
		}
		public MailSender subject(String subject) {
			this.subject = subject;
			return this;
		}
		public MailSender content(String content) {
			this.content = content;
			return this;
		}
		public MailSender file(String filePath) {
			this.attachfile = filePath;
			return this;
		}
		public MailSender asyn() {
			this.asyn = true;
			return this;
		}

		// 执行发送
		public void send() {
			if (this.asyn) {
				asynMail(this);
			} else {
				sendMail(this);
			}
		}
	}

}
