/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.rule;

import static java.lang.String.format;

import io.harness.NoopStatement;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.CategoryConfigException;
import io.harness.rule.UserInfo.UserInfoBuilder;
import io.harness.scm.ScmSecret;
import io.harness.scm.SecretName;

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.rcarz.jiraclient.BasicCredentials;
import net.rcarz.jiraclient.Issue;
import net.rcarz.jiraclient.JiraClient;
import net.rcarz.jiraclient.JiraException;
import org.junit.Ignore;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

@Slf4j
@OwnedBy(HarnessTeam.DX)
public class OwnerRule implements TestRule {
  @Getter(lazy = true) private static final JiraClient jira = connect();
  public static final String ASSIGNEE = "assignee";
  public static final String SUMMARY = "summary";
  public static final String NEEDS_FIXING = "needs fixing";
  public static final String PRIORITY = "priority";
  public static final String PRIORITY_VALUE0 = "P0";
  public static final String PRIORITY_VALUE1 = "P1";
  public static final String DESCRIPTION = "description";
  public static final String DESCRIPTION_VALUE =
      "This is auto generated jira issue for tracking the fix of the test in the title.";

  private static JiraClient connect() {
    ScmSecret scmSecret = new ScmSecret();
    String jiraTestUser = scmSecret.decryptToString(SecretName.builder().value("jira_test_user").build());
    String jiraTestPassword = scmSecret.decryptToString(SecretName.builder().value("jira_test_password").build());
    BasicCredentials credentials = new BasicCredentials(jiraTestUser, jiraTestPassword);
    try {
      return new JiraClient("https://harness.atlassian.net", credentials);
    } catch (JiraException e) {
      log.error("Failed to connect to jira", e);
    }

    return null;
  }

  public static final String GHPRB_PULL_AUTHOR_EMAIL = "ghprbPullAuthorEmail";

  public static final String CDC = "CDC";
  public static final String CDP = "CDP";
  public static final String CE = "CE";
  public static final String CI = "CI";
  public static final String CIT = "CIT";
  public static final String CV = "CV";
  public static final String DEL = "DEL";
  public static final String DX = "DX";
  public static final String PL = "PL";
  public static final String SWAT = "SWAT";
  public static final String GTM = "GTM";

  public static final String AADITI = "aaditi.joag";
  public static final String ABHIJITH = "abhijith.pradeep";
  public static final String ABHINAV = "abhinav.singh";
  public static final String ABHINAV2 = "abhinav.singh2";
  public static final String ACHYUTH = "achyuth.prakash";
  public static final String ADWAIT = "adwait.bhandare";
  public static final String ADARSH = "adarsh.agarwal";
  public static final String AKRITI = "akriti.garg";
  public static final String ALEKSANDAR = "aleksandar.radisavljevic";
  public static final String AGORODETKI = "alexandr.gorodetki";
  public static final String ALEXEI = "alexei.stirbul";
  public static final String AMAN = "aman.singh";
  public static final String AMIT = "amit.jambure";
  public static final String ANJAN = "anjan.balgovind";
  public static final String ANGELO = "angelo.rodriguez";
  public static final String ANKIT = "ankit.singhal";
  public static final String ANKUSH = "ankush.shaw";
  public static final String ANSHUL = "anshul";
  public static final String ANUBHAW = "anubhaw";
  public static final String ARPIT = "arpit.tiwari";
  public static final String ARVIND = "arvind.choudhary";
  public static final String AVMOHAN = "abhijith.mohan";
  public static final String BOGDAN = "bogdan.azaric";
  public static final String BOJAN = "bojan.micovic";
  public static final String BRETT = "brett";
  public static final String BRIJESH = "brijesh.dhakar";
  public static final String DEEPAK = "deepak.patankar";
  public static final String DHRUV = "dhruv.upadhyay";
  public static final String DINESH = "dinesh.garg";
  public static final String FILIP = "filip.petrovic";
  public static final String GARVIT = "garvit.pahal";
  public static final String GEORGE = "george";
  public static final String GUNA = "guna.chandrasekaran";
  public static final String HANTANG = "hannah.tang";
  public static final String HARI = "srihari.chidella";
  public static final String HARSH = "harsh.jain";
  public static final String HITESH = "hitesh.aringa";
  public static final String IGOR = "igor.gere";
  public static final String INDER = "inderpreet.chera";
  public static final String JENNY = "jenny.james";
  public static final String JUHI = "juhi.agrawal";
  public static final String KAMAL = "kamal.joshi";
  public static final String KARAN = "karan.siwach";
  public static final String MARKO = "marko.barjaktarovic";
  public static final String MLUKIC = "marko.lukic";
  public static final String MARKOM = "marko.milic";
  public static final String MATT = "matthew.lin";
  public static final String MEENAKSHI = "meenakshi.raikwar";
  public static final String MEHUL = "mehul.kasliwal";
  public static final String MILOS = "milos.paunovic";
  public static final String MOHIT = "mohit.kurani";
  public static final String NATHAN = "nathan.nguyen";
  public static final String NATARAJA = "nataraja";
  public static final String NGONZALEZ = "nasser.gonzalez";
  public static final String NEMANJA = "nemanja.lukovic";
  public static final String NIKOLA = "nikola.obucina";
  public static final String NIKUNJ = "nikunj.badjatya";
  public static final String NISHANT = "nishant.saini";
  public static final String PARNIAN = "parnian";
  public static final String PARDHA = "sripardha.chidella";
  public static final String PIYUSH = "piyush.patel";
  public static final String POOJA = "pooja";
  public static final String PRANJAL = "pranjal";
  public static final String PRASHANT = "prashant.pal";
  public static final String PRASHANTSHARMA = "prashant.sharma";
  public static final String PRATEEK = "prateek.barapatre";
  public static final String PRAVEEN = "praveen.sugavanam";
  public static final String PUNEET = "puneet.saraswat";
  public static final String RAGHU = "raghu";
  public static final String RAJ = "raj.patel";
  public static final String RAMA = "rama";
  public static final String RAUNAK = "raunak.agrawal";
  public static final String REETIKA = "mallavarapu.reetika";
  public static final String ROGER = "roger.simms";
  public static final String ROHIT = "rohit.reddy";
  public static final String ROHIT_KUMAR = "rohit.kumar";
  public static final String ROHITKARELIA = "rohit.karelia";
  public static final String RUSHABH = "rushabh.shah";
  public static final String SAGNIK = "sagnik.de";
  public static final String SAMARTH = "samarth.singhal";
  public static final String SANDESH = "sandesh.katta";
  public static final String SANJA = "sanja.jokic";
  public static final String SANYASI_NAIDU = "sanyasi.naidu";
  public static final String SEAN = "sean.dunne";
  public static final String SHASWAT = "shaswat.deep";
  public static final String SHIVAKUMAR = "shivakumar.ningappa";
  public static final String SHUBHANSHU = "shubhanshu.verma";
  public static final String SOWMYA = "sowmya.k";
  public static final String SRINIVAS = "srinivas";
  public static final String SRIRAM = "sriram";
  public static final String SUJAY = "sujay.sharma";
  public static final String SATYAM = "satyam.shanker";
  public static final String UJJAWAL = "ujjawal.prasad";
  public static final String UTKARSH = "utkarsh.gupta";
  public static final String UTSAV = "utsav.krishnan";
  public static final String VAIBHAV_SI = "vaibhav.si";
  public static final String VENKATESH = "venkatesh.kotrike";
  public static final String VIKAS = "vikas.naiyar";
  public static final String VIKAS_S = "vikas.singh";
  public static final String VIKAS_M = "vikas.maddukuri";
  public static final String VISTAAR = "vistaar.juneja";
  public static final String VLAD = "vladimir.peric";
  public static final String VOJIN = "vojin.djukic";
  public static final String VUK = "vuk.skobalj";
  public static final String XIN = "xin.shao";
  public static final String YOGESH = "yogesh.chauhan";
  public static final String ZHUO = "zhuo.yin";
  public static final String VARDAN_BANSAL = "vardan.bansal";
  public static final String NANDAN = "nandan.chandrashekar";
  public static final String RIHAZ = "rihaz.zahir";
  public static final String PRABU = "prabu.rajendran";
  public static final String MOUNIK = "mounik.vvss";
  public static final String PHOENIKX = "nikhil.ranjan";
  public static final String SHUBHAM = "shubham.agrawal";
  public static final String ABOSII = "alexandru.bosii";
  public static final String ACASIAN = "alexandru.casian";
  public static final String ANIL = "anil.chowdhury";
  public static final String VGLIJIN = "vasile.glijin";
  public static final String MILAN = "milan.balaban";
  public static final String RAGHVENDRA = "raghvendra.singh";
  public static final String ARCHIT = "archit.singla";
  public static final String IVAN = "ivan.mijailovic";
  public static final String SAHIL = "sahil.hindwani";
  public static final String BOJANA = "bojana.milovanovic";
  public static final String LAZAR = "lazar.matovic";
  public static final String HINGER = "abhinav.hinger";
  public static final String TATHAGAT = "tathagat.chaurasiya";
  public static final String TMACARI = "tudor.macari";
  public static final String NAMAN = "naman.verma";
  public static final String NAMAN_TALAYCHA = "naman.talaycha";
  public static final String DEEPAK_PUTHRAYA = "deepak.puthraya";
  public static final String LUCAS = "lucas.mari";
  public static final String NICOLAS = "nicolas.bantar";
  public static final String SAINATH = "sainath.batthala";
  public static final String MOHIT_GARG = "mohit.garg";
  public static final String AKASH_NAGARAJAN = "akash.nagarajan";
  public static final String KANHAIYA = "kanhaiya.rathi";
  public static final String ABHINAV_MITTAL = "abhinav.mittal";
  public static final String SHASHANK = "shashank.singh";
  public static final String PRAKHAR = "prakhar.saxena";
  public static final String JASMEET = "jasmeet.saini";
  public static final String MUNISH = "munish.jalota";
  public static final String MEET = "rathod.meetsatish";
  public static final String RIYASYASH = "riyas.yash";
  public static final String VED = "ved.atultambat";
  public static final String JAMIE = "tianyi.li";
  public static final String ALEXANDRU_CIOFU = "alexandru.ciofu";
  public static final String NANA_XU = "nana.xu";
  public static final String JELENA = "jelena.arsenijevic";
  public static final String BHAVYA = "bhavya.agrawal";
  public static final String BHARAT_GOEL = "bharat.goel";
  public static final String DEEPAK_CHHIKARA = "deepak.chhikara";
  public static final String KAPIL = "kapil.choudhary";
  public static final String PAVIC = "slobodan.pavic";
  public static final String SHIVAM = "shivam.negi";
  public static final String VITALIE = "vitalie.safronovici";
  public static final String SHUBHAM_MAHESHWARI = "shubham.maheshwari";

  @Deprecated public static final String UNKNOWN = "unknown";

  private static UserInfoBuilder defaultUserInfo(String user) {
    return UserInfo.builder().email(user + "@harness.io").jira(user);
  }

  private static final Map<String, UserInfo> active =
      ImmutableMap.<String, UserInfo>builder()
          .put(AADITI, defaultUserInfo(AADITI).slack("UCFPUNRAQ").team(CDC).build())
          .put(ABHIJITH, defaultUserInfo(ABHIJITH).slack("U0270PE0T24").team(CV).build())
          .put(ABHINAV, defaultUserInfo(ABHINAV).slack("UQQPR8M6Y").team(DX).build())
          .put(ACHYUTH, defaultUserInfo(ACHYUTH).slack("U024FAWEZL7").team(CDP).build())
          .put(ADARSH, defaultUserInfo(ADARSH).slack("U01Q02K5LUR").team(CE).build())
          .put(ALEKSANDAR, defaultUserInfo(ALEKSANDAR).slack("U012MKR5FUZ").team(CI).build())
          .put(AGORODETKI, defaultUserInfo(AGORODETKI).slack("U013KM8H2NL").team(CDC).build())
          .put(AKRITI, defaultUserInfo(AKRITI).slack("U01JCQAS84S").team(PL).build())
          .put(ALEXEI, defaultUserInfo(ALEXEI).slack("U012VS112EN").team(CDC).build())
          .put(AMAN, defaultUserInfo(AMAN).slack("UDJG47CHF").team(PL).build())
          .put(AMIT, defaultUserInfo(AMIT).slack("U028DMTKVHN").team(CE).build())
          .put(ANGELO, defaultUserInfo(ANGELO).slack("U02MA4U1B4M").team(CV).build())
          .put(ANJAN, defaultUserInfo(ANJAN).slack("UCUMEJ0FK").team(CV).build())
          .put(ANKIT, defaultUserInfo(ANKIT).slack("UF76W0NN5").team(PL).build())
          .put(ANKUSH, defaultUserInfo(ANKUSH).slack("U016VE8EP7X").team(PL).build())
          .put(ANSHUL, defaultUserInfo(ANSHUL).slack("UASUA3E65").team(CDP).build())
          .put(ANUBHAW, defaultUserInfo(ANUBHAW).slack("U0Z1U0HNW").team(CDP).build())
          .put(ARPIT, defaultUserInfo(ARPIT).slack("U0221TUJ4QK").team(DEL).build())
          .put(ARVIND, defaultUserInfo(ARVIND).slack("U01542TQGCU").team(CDP).build())
          .put(AVMOHAN, defaultUserInfo(AVMOHAN).slack("UK72UTBJR").team(CE).build())
          .put(BOGDAN, defaultUserInfo(BOGDAN).slack("U026QDGCDQA").team(CDP).build())
          .put(BOJAN, defaultUserInfo(BOJAN).slack("U0225T2NT1A").team(DEL).build())
          .put(BRETT, defaultUserInfo(BRETT).slack("U40VBHCGH").team(SWAT).build())
          .put(BRIJESH, defaultUserInfo(BRIJESH).slack("U015LRWS8KV").team(PL).build())
          .put(DEEPAK, defaultUserInfo(DEEPAK).slack("UK9EKBKQS").team(DX).build())
          .put(DHRUV, defaultUserInfo(DHRUV).slack("U012WHYL7G8").team(CDC).build())
          .put(DINESH, UserInfo.builder().email("dinesh.garg@harness.io").slack("UQ0DMQG11").build())
          .put(GARVIT, defaultUserInfo(GARVIT).slack("UHH98EXDK").team(CDC).build())
          .put(GEORGE, defaultUserInfo(GEORGE).slack("U88CA877V").team(DEL).build())
          .put(GUNA, defaultUserInfo(GUNA).slack("UD45NV41W").team(DEL).build())
          .put(HANTANG, defaultUserInfo(HANTANG).slack("UK8AQJSCS").team(CE).build())
          .put(HARI, defaultUserInfo(HARI).slack("U01KKD4NSUQ").team(DX).build())
          .put(PARDHA, defaultUserInfo(PARDHA).slack("U01JVHQ61TL").team(CDP).build())
          .put(HARSH, defaultUserInfo(HARSH).slack("UJ1CDM3FY").team(CDC).build())
          .put(HITESH, defaultUserInfo(HITESH).slack("UK41C9QJH").team(CE).build())
          .put(IGOR, defaultUserInfo(IGOR).slack("U0104P0SC03").team(DX).build())
          .put(INDER, defaultUserInfo(INDER).slack("U0155TBCW7R").team(CDC).build())
          .put(JENNY, defaultUserInfo(JENNY).slack("U01VA4SBXU0").team(DEL).build())
          .put(JUHI, defaultUserInfo(JUHI).slack("UL1KX4K1S").build())
          .put(KAMAL, defaultUserInfo(KAMAL).slack("UKFQ1PQBH").team(CV).build())
          .put(KANHAIYA, defaultUserInfo(KANHAIYA).slack("U01HFBAT8A2").team(CV).build())
          .put(KAPIL, defaultUserInfo(KAPIL).slack("U02E2J40E21").team(CV).build())
          .put(KARAN, defaultUserInfo(KARAN).slack("U015LRX21FD").team(PL).build())
          .put(LUCAS, defaultUserInfo(LUCAS).slack("U01C8MPTS2J").team(DEL).build())
          .put(MARKO, defaultUserInfo(MARKO).slack("UVDT91N9W").team(DEL).build())
          .put(MARKOM, defaultUserInfo(MARKOM).slack("U022NV33SF7").team(DEL).build())
          .put(MATT, defaultUserInfo(MATT).slack("U019QR7TA7M").team(DEL).build())
          .put(MEENAKSHI, UserInfo.builder().email("meenakshi.raikwar@harness.io").slack("UKP2AEUNA").build())
          .put(MEHUL, defaultUserInfo(MEHUL).slack("URYP18AHX").team(PL).build())
          .put(MILOS, defaultUserInfo(MILOS).slack("U014WS8CXEV").team(CDC).build())
          .put(MOHIT, defaultUserInfo(MOHIT).slack("USB6NTE22").team(PL).build())
          .put(NATHAN, defaultUserInfo(NATHAN).slack("U01NNN1T4CV").team(GTM).build())
          .put(NATARAJA, defaultUserInfo(NATARAJA).slack("UDQAS9J5C").team(PL).build())
          .put(NEMANJA, defaultUserInfo(NEMANJA).slack("U016F8DDQSC").team(CV).build())
          .put(NICOLAS, defaultUserInfo(NICOLAS).slack("U01C8MPQVQE").team(DEL).build())
          .put(NIKOLA, defaultUserInfo(NIKOLA).slack("U011CFJ4YDV").team(PL).build())
          .put(NIKUNJ, defaultUserInfo(NIKUNJ).slack("U019JUP10AF").team(CE).build())
          .put(NISHANT, defaultUserInfo(NISHANT).slack("U02MBSJ8E79").team(PL).build())
          .put(PARNIAN, defaultUserInfo(PARNIAN).slack("U89A5MLQK").team(CV).build())
          .put(POOJA, defaultUserInfo(POOJA).slack("UDDA9L0D6").team(CDC).build())
          .put(PRANJAL, defaultUserInfo(PRANJAL).slack("UBV049Q5B").team(SWAT).build())
          .put(PRASHANT, defaultUserInfo(PRASHANT).slack("UJLBB7ULT").team(CDC).build())
          .put(PRATEEK, defaultUserInfo(PRATEEK).slack("U02LG0DA988").team(PL).build())
          .put(PRAVEEN, defaultUserInfo(PRAVEEN).slack("UAQH9QHSB").team(CV).build())
          .put(PUNEET, defaultUserInfo(PUNEET).slack("U8PMB1XKM").team(CE).build())
          .put(PRASHANTSHARMA, defaultUserInfo(PRASHANTSHARMA).slack("U015LRWKR6X").team(PL).build())
          .put(RAGHU, defaultUserInfo(RAGHU).slack("U4Z2PG2TD").team(CV).build())
          .put(RAJ, defaultUserInfo(RAJ).slack("U01CPMHBX27").team(PL).build())
          .put(RAUNAK, defaultUserInfo(RAUNAK).slack("UU06GNQ0M").team(CDP).build())
          .put(RAMA, defaultUserInfo(RAMA).slack("U69BLRG72").team(DX).build())
          .put(REETIKA, defaultUserInfo(REETIKA).slack("U0164D4BV0A").team(PL).build())
          .put(ROHIT, defaultUserInfo(ROHIT).slack("UKLSUUCAC").team(CE).build())
          .put(ROHIT_KUMAR, defaultUserInfo(ROHIT_KUMAR).slack("UL92UJN4S").team(DX).build())
          .put(ROHITKARELIA, defaultUserInfo(ROHITKARELIA).slack("UP48HU3T9").team(SWAT).build())
          .put(RUSHABH, defaultUserInfo(RUSHABH).slack("U8M736D36").team(PL).jira("rushabh").build())
          .put(ADWAIT, defaultUserInfo(ADWAIT).slack("U8PL7JRMG").team(CDP).build())
          .put(SATYAM, defaultUserInfo(SATYAM).slack("U9Z3R0GL8").team(CDP).build())
          .put(ANIL, defaultUserInfo(ANIL).slack("U0132ESPZ08").team(CDP).build())
          .put(RAGHVENDRA, defaultUserInfo(RAGHVENDRA).slack("U012F7A157Y").team(CDP).build())
          .put(SAMARTH, defaultUserInfo(SAMARTH).slack("U01KNQ4S20J").team(PL).build())
          .put(SANDESH, defaultUserInfo(SANDESH).slack("U015PLPSD47").team(CE).build())
          .put(SANJA, defaultUserInfo(SANJA).slack("U015Q24465T").team(DEL).build())
          .put(SANYASI_NAIDU, defaultUserInfo(SANYASI_NAIDU).slack("U012P5KH3RU").team(DX).build())
          .put(SEAN, defaultUserInfo(SEAN).slack("U020VELFZNK").team(DEL).build())
          .put(SHASWAT, UserInfo.builder().email("shaswat.deep@harness.io").slack("UL9J5EH7A").build())
          .put(SHIVAKUMAR, defaultUserInfo(SHIVAKUMAR).slack("U01124VC43C").team(CI).build())
          .put(SHUBHANSHU, defaultUserInfo(SHUBHANSHU).slack("UKLTRSAN9").team(CE).build())
          .put(SOWMYA, defaultUserInfo(SOWMYA).slack("UHM19HBKM").team(CV).build())
          .put(SRINIVAS, defaultUserInfo(SRINIVAS).slack("U4QC23961").team(CDC).build())
          .put(SRIRAM, defaultUserInfo(SRIRAM).slack("U5L475PK5").team(CV).build())
          .put(SUJAY, defaultUserInfo(SUJAY).slack("U01J9MF7SMT").team(CDC).build())
          .put(UJJAWAL, defaultUserInfo(UJJAWAL).slack("UKLSV01DW").team(PL).build())
          .put(UTKARSH, defaultUserInfo(UTKARSH).slack("UKGF0UL58").team(PL).build())
          .put(UTSAV, defaultUserInfo(UTSAV).slack("U015DTMQRV4").team(CE).build())
          .put(VAIBHAV_SI, defaultUserInfo(VAIBHAV_SI).slack("UCK76T36U").team(CDP).build())
          .put(VENKATESH, UserInfo.builder().email("venkatesh.kotrike@harness.io").slack("UGF55UEHF").build())
          .put(VIKAS, defaultUserInfo(VIKAS).slack("UE7M4CNMA").team(PL).build())
          .put(VIKAS_S, defaultUserInfo(VIKAS_S).slack("U01HMK9SY2V").team(CDC).build())
          .put(VIKAS_M, defaultUserInfo(VIKAS_M).slack("U0257UVS11T").team(PL).build())
          .put(VOJIN, defaultUserInfo(VOJIN).slack("U015TFFL83G").team(PL).build())
          .put(VISTAAR, defaultUserInfo(VISTAAR).slack("U0138Q1JEHM").team(CI).build())
          .put(VUK, defaultUserInfo(VUK).slack("U0115RT3EQL").team(DEL).build())
          .put(XIN, defaultUserInfo(XIN).slack("U01R3KSP3M1").team(DEL).build())
          .put(YOGESH, defaultUserInfo(YOGESH).slack("UJVLUUXAT").team(CDP).build())
          .put(ZHUO, defaultUserInfo(ZHUO).slack("U01QCSY8518").team(GTM).build())
          .put(VARDAN_BANSAL, defaultUserInfo(VARDAN_BANSAL).slack("UH8NYAAUU").team(DX).build())
          .put(NANDAN, defaultUserInfo(NANDAN).slack("UKMS5KCBS").team(CV).build())
          .put(RIHAZ, defaultUserInfo(RIHAZ).slack("USUP66518").team(CDP).build())
          .put(MOUNIK, defaultUserInfo(MOUNIK).slack("U024PB2UQ86").team(CDC).build())
          .put(PRABU, defaultUserInfo(PRABU).slack("UTF8GHZEK").team(CDC).build())
          .put(PHOENIKX, defaultUserInfo(PHOENIKX).slack("URWLPPJ8Z").team(PL).build())
          .put(SHUBHAM, defaultUserInfo(SHUBHAM).slack("UTRFKPL57").team(CI).build())
          .put(ABOSII, defaultUserInfo(ABOSII).slack("U01321VFH1A").team(CDP).build())
          .put(ACASIAN, defaultUserInfo(ACASIAN).slack("U012UEVAPAR").team(CDP).build())
          .put(VGLIJIN, defaultUserInfo(VGLIJIN).slack("U012W2S777V").team(CDC).build())
          .put(MILAN, defaultUserInfo(MILAN).slack("U012P4GHM7Y").team(CDC).build())
          .put(ARCHIT, defaultUserInfo(ARCHIT).slack("U012QGPR9N0").team(CDC).build())
          .put(IVAN, defaultUserInfo(IVAN).slack("U014BFQ9PJS").team(CDP).build())
          .put(TATHAGAT, defaultUserInfo(TATHAGAT).slack("U015DTMJLA2").team(CDP).build())
          .put(NAMAN_TALAYCHA, defaultUserInfo(NAMAN_TALAYCHA).slack("U021UU5UT46").team(CDP).build())
          .put(SAHIL, defaultUserInfo(SAHIL).slack("U0141LFMEF8").team(CDP).build())
          .put(BOJANA, defaultUserInfo(BOJANA).slack("U014GS4NFLM").team(CDP).build())
          .put(LAZAR, defaultUserInfo(LAZAR).slack("U0150TB4LSK").team(DEL).build())
          .put(HINGER, defaultUserInfo(HINGER).slack("U015DTMV2A2").team(CDC).build())
          .put(UNKNOWN, UserInfo.builder().email("n/a").slack("channel").build())
          .put(TMACARI, defaultUserInfo(TMACARI).slack("U0172NX6SGY").team(CDP).build())
          .put(NAMAN, defaultUserInfo(NAMAN).slack("U0173M823CN").team(CDP).build())
          .put(DEEPAK_PUTHRAYA, defaultUserInfo(DEEPAK_PUTHRAYA).slack("U018SBXS5M1").team(CDC).build())
          .put(SAINATH, defaultUserInfo(SAINATH).slack("U01FN5EH3PF").team(CDP).build())
          .put(MOHIT_GARG, defaultUserInfo(MOHIT_GARG).slack("U01F9DBKYA3").team(DX).build())
          .put(AKASH_NAGARAJAN, defaultUserInfo(AKASH_NAGARAJAN).slack("U01GDEF032R").team(DX).build())
          .put(PIYUSH, defaultUserInfo(PIYUSH).slack("U01G9QYDMAA").team(PL).build())
          .put(ABHINAV_MITTAL, defaultUserInfo(ABHINAV_MITTAL).slack("U01Q893534N").team(CDC).build())
          .put(SHASHANK, defaultUserInfo(SHASHANK).slack("U01S8B29PFD").team(PL).build())
          .put(VLAD, defaultUserInfo(VLAD).slack("U0209EGCC49").team(DEL).build())
          .put(PRAKHAR, defaultUserInfo(PRAKHAR).slack("U01U399V1PW").team(CDP).build())
          .put(JASMEET, defaultUserInfo(JASMEET).slack("U01LAM57V5K").team(DX).build())
          .put(MUNISH, defaultUserInfo(MUNISH).slack("U01U6B4DF7U").team(CE).build())
          .put(MEET, defaultUserInfo(MEET).slack("U021LUASDL7").team(DX).build())
          .put(RIYASYASH, defaultUserInfo(RIYASYASH).slack("U01EM1JJE0H").team(CE).build())
          .put(VED, defaultUserInfo(VED).slack("U024FBHS295").team(CDC).build())
          .put(JAMIE, defaultUserInfo(JAMIE).slack("U028ZCC3GHK").team(CDC).build())
          .put(ALEXANDRU_CIOFU, defaultUserInfo(ALEXANDRU_CIOFU).slack("U025JKQMRSA").team(DX).build())
          .put(NANA_XU, defaultUserInfo(NANA_XU).slack("U01QWLCSUHL").team(GTM).build())
          .put(JELENA, defaultUserInfo(JELENA).slack("U02AGRC9D0S").team(CDP).build())
          .put(FILIP, defaultUserInfo(FILIP).slack("U02E3L2S803").team(CDP).build())
          .put(ABHINAV2, defaultUserInfo(ABHINAV2).slack("U02D3KJKF33").team(CDP).build())
          .put(BHAVYA, defaultUserInfo(BHAVYA).slack("U024WB5P1NF").team(PL).build())
          .put(MLUKIC, defaultUserInfo(MLUKIC).slack("U02DS8RGUSV").team(CDP).build())
          .put(BHARAT_GOEL, defaultUserInfo(BHARAT_GOEL).slack("U02053C1QMA").team(DEL).build())
          .put(DEEPAK_CHHIKARA, defaultUserInfo(DEEPAK_CHHIKARA).slack("U02D8V7PVFG").team(CV).build())
          .put(PAVIC, defaultUserInfo(PAVIC).slack("U02GPSRJB40").team(CV).build())
          .put(SHIVAM, defaultUserInfo(SHIVAM).slack("U01CPMYC37T").team(CDC).build())
          .put(VITALIE, defaultUserInfo(VITALIE).slack("U02U09LU91R").team(CDP).build())
          .build();

  private static String prDeveloperId = findDeveloperId(System.getenv(GHPRB_PULL_AUTHOR_EMAIL));

  private static final Map<String, TeamInfo> teamInfo =
      ImmutableMap.<String, TeamInfo>builder()
          .put(CDC, TeamInfo.builder().team(CDC).leader(PRASHANT).leader(POOJA).build())
          .put(CDP, TeamInfo.builder().team(CDP).leader(ANSHUL).build())
          .put(CE, TeamInfo.builder().team(CE).leader(PUNEET).leader(AVMOHAN).build())
          .put(CI, TeamInfo.builder().team(CI).leader(SHIVAKUMAR).build())
          .put(CIT, TeamInfo.builder().team(CIT).leader(ROGER).build())
          .put(CV, TeamInfo.builder().team(CV).leader(RAGHU).build())
          .put(DEL, TeamInfo.builder().team(DEL).leader(GEORGE).leader(MARKO).build())
          .put(DX, TeamInfo.builder().team(DX).leader(RAMA).build())
          .put(PL, TeamInfo.builder().team(PL).leader(ANKIT).leader(VIKAS).build())
          .put(SWAT, TeamInfo.builder().team(SWAT).leader(BRETT).build())
          .put(GTM, TeamInfo.builder().team(GTM).leader(RAMA).build())
          .build();

  @Override
  public Statement apply(Statement statement, Description description) {
    Owner owner = description.getAnnotation(Owner.class);
    if (owner == null) {
      throw new CategoryConfigException("Owner annotation is obligatory.");
    }

    Ignore ignore = description.getAnnotation(Ignore.class);
    if (System.getenv("SONAR_TOKEN") != null) {
      if (owner.intermittent()) {
        checkForJira(description.getDisplayName(), owner.developers()[0], PRIORITY_VALUE1);
      }

      if (ignore != null) {
        checkForJira(description.getDisplayName(), owner.developers()[0], PRIORITY_VALUE1);
      }
    }

    for (String developer : owner.developers()) {
      if (!active.containsKey(developer)) {
        throw new CategoryConfigException(format("Developer %s is not active.", developer));
      }

      if (owner.intermittent()) {
        fileOwnerAs(developer, "intermittent");
      }

      if (ignore != null) {
        fileOwnerAs(developer, "ignore");
      }
    }

    if (prDeveloperId == null || !Arrays.asList(owner.developers()).contains(prDeveloperId)) {
      if (owner.intermittent()) {
        return new NoopStatement();
      }
    }
    return statement;
  }

  public static UserInfo findDeveloper(String developerId) {
    return active.get(developerId);
  }

  public static String findDeveloperId(String email) {
    if (email == null) {
      return null;
    }

    for (Entry<String, UserInfo> entry : active.entrySet()) {
      if (entry.getValue().getEmail().equals(email)) {
        return entry.getKey();
      }
    }

    return null;
  }

  private static String generateJQL(String test) {
    return format("type = Bug"
            + " AND statusCategory != Done"
            + " AND %s ~ \"%s\""
            + " AND %s ~ \"%s\"",
        SUMMARY, test, SUMMARY, NEEDS_FIXING);
  }

  public static void checkForJira(String test, String developer, String priority) {
    if (!"true".equals(System.getenv("ENABLE_TEST_JIRA_REPORTS"))) {
      return;
    }

    UserInfo userInfo = active.get(developer);
    if (userInfo == null) {
      return;
    }

    try {
      JiraClient jira = getJira();
      String jql = generateJQL(test);
      Issue.SearchResult searchResult = jira.searchIssues(jql, 1);
      if (searchResult.total == 0) {
        if (userInfo.getJira() == null || userInfo.getTeam() == null) {
          return;
        }

        Issue issue = generateJiraCreate(test, userInfo, priority).execute();
        log.info("New jira issue was created {}", issue.getKey());
        return;
      }

      Issue issue = searchResult.issues.get(0);

      if (!issue.getProject().getKey().equals(userInfo.getTeam())) {
        // We cannot automatically move an issue from one project to another.
        // Instead we are going to mark the current one as rejected.
        // Next time we would not find it and we will create a new one.

        // First lets set Bug Resolution to Ownership changed
        issue.update().field("customfield_10687", "Ownership changed").execute();

        issue.transition().execute("Rejected");
        return;
      }

      if (userInfo.getJira() != null && !issue.getAssignee().getEmail().equals(userInfo.getEmail())) {
        issue.update().field(ASSIGNEE, userInfo.getJira()).execute();
      }

      if (priority.compareTo(issue.getPriority().getName()) > 0) {
        issue.update().field(PRIORITY, priority).execute();
      }

    } catch (JiraException e) {
      log.error("Failed when checking the jira issue", e);
    }
  }

  private static Issue.FluentCreate generateJiraCreate(String test, UserInfo userInfo, String priority)
      throws JiraException {
    return getJira()
        .createIssue(userInfo.getTeam(), "Bug")
        .field(ASSIGNEE, userInfo.getJira())
        .field(SUMMARY, test + " " + NEEDS_FIXING)
        .field(PRIORITY, priority)
        .field(DESCRIPTION, DESCRIPTION_VALUE);
  }

  public static void fileOwnerAs(String developer, String type) {
    log.info("Developer {} is found to be owner of {} test", developer, type);
    createSlackFile(developer, type);

    UserInfo userInfo = active.get(developer);
    if (userInfo == null) {
      return;
    }

    TeamInfo teamLeader = teamInfo.get(userInfo.getTeam());
    if (teamLeader == null) {
      return;
    }

    for (String leader : teamLeader.getLeaders()) {
      if (!leader.equals(developer)) {
        fileLeaderAs(leader, type);
      }
    }
  }

  public static void fileLeaderAs(String leader, String type) {
    createSlackFile(leader, type + "-leaders");
  }

  private static String root =
      System.getenv().getOrDefault("TEST_OWNERS_ROOT_DIR", System.getProperty("java.io.tmpdir") + "/owners");

  private static void createSlackFile(String developer, String directory) {
    UserInfo userInfo = active.get(developer);
    if (userInfo == null) {
      return;
    }
    String identify = userInfo.getSlack() == null ? developer : "<@" + userInfo.getSlack() + ">";

    try {
      File file = new File(format("%s/%s/%s", root, directory, identify));

      file.getParentFile().mkdirs();
      if (file.createNewFile()) {
        log.info("Wrote to {}", file);
      } else {
        log.debug("{} was already set", file);
      }
    } catch (Exception ignore) {
      // Ignore the exceptions
    }
  }
}
