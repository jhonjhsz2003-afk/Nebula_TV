package br.com.nebulatv.model;
public class Account {
    public long id; public String username; public String email; public String displayName; public boolean admin;
    public Account() {}
    public Account(long id,String username,String email,String displayName,boolean admin){this.id=id;this.username=username;this.email=email;this.displayName=displayName;this.admin=admin;}
}
