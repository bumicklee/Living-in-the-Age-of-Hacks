package io.github.securekit.examples.demo;

import io.github.securekit.core.crypto.Encrypted;

/** 저장소에 두는 개인정보 한 건. phone은 암호문으로, phoneIndex는 검색용 해시로 들어간다 */
public class PiiRecord {
    private long id;

    @Encrypted(blindIndex = "phoneIndex")
    private String phone;

    private String phoneIndex;

    public PiiRecord() {
    }

    public PiiRecord(long id, String phone) {
        this.id = id;
        this.phone = phone;
    }

    public PiiRecord copy() {
        PiiRecord c = new PiiRecord(id, phone);
        c.phoneIndex = phoneIndex;
        return c;
    }

    public long getId() { return id; }
    public String getPhone() { return phone; }
    public String getPhoneIndex() { return phoneIndex; }
}
