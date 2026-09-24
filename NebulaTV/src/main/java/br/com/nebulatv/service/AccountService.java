package br.com.nebulatv.service;

import br.com.nebulatv.dao.Database;
import br.com.nebulatv.model.Account;
import br.com.nebulatv.model.Profile;
import br.com.nebulatv.util.HashUtil;
import java.sql.*; import java.util.*;

public final class AccountService {
    private Account current;
    public Account current(){return current;}
    public boolean hasAccounts(){try(PreparedStatement p=Database.conn().prepareStatement("SELECT COUNT(*) FROM accounts WHERE username<>?")){p.setString(1,"demo");try(ResultSet r=p.executeQuery()){return r.next()&&r.getInt(1)>0;}}catch(Exception e){return false;}}
    private boolean firstRealAccount(){return !hasAccounts();}
    public Account register(String username,String email,String displayName,String password,String avatar){
        username=username.trim(); email=email.trim().toLowerCase(); displayName=displayName.trim();
        if(username.length()<3||email.length()<5||password.length()<4||displayName.isBlank()) throw new IllegalArgumentException("Preencha todos os campos corretamente.");
        try(PreparedStatement p=Database.conn().prepareStatement("INSERT INTO accounts(username,email,display_name,password_hash,admin) VALUES(?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){
            boolean admin=firstRealAccount(); p.setString(1,username);p.setString(2,email);p.setString(3,displayName);p.setString(4,HashUtil.sha256(password));p.setInt(5,admin?1:0);p.executeUpdate();
            try(ResultSet r=p.getGeneratedKeys()){r.next(); long id=r.getLong(1); createDefaultProfile(id,displayName,avatar); current=new Account(id,username,email,displayName,admin); return current;}
        }catch(SQLException e){ if(e.getMessage()!=null && e.getMessage().contains("UNIQUE")) throw new IllegalArgumentException("Usuário ou e-mail já cadastrado."); throw new RuntimeException(e); }
    }
    public Account login(String login,String password){
        try(PreparedStatement p=Database.conn().prepareStatement("SELECT id,username,email,display_name,admin FROM accounts WHERE (username=? OR email=?) AND password_hash=?")){
            p.setString(1,login.trim());p.setString(2,login.trim().toLowerCase());p.setString(3,HashUtil.sha256(password));
            try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Usuário/e-mail ou senha inválidos.");current=new Account(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getInt(5)==1);return current;}
        }catch(SQLException e){throw new RuntimeException(e);}
    }
    public List<Profile> profiles(){
        List<Profile> a=new ArrayList<>(); if(current==null)return a;
        try(PreparedStatement p=Database.conn().prepareStatement("SELECT id,user_id,name,avatar,child,pin FROM profiles WHERE user_id=? ORDER BY id")){p.setLong(1,current.id);try(ResultSet r=p.executeQuery()){while(r.next())a.add(new Profile(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),r.getInt(5)==1,r.getString(6)));}}catch(Exception e){throw new RuntimeException(e);}return a;
    }
    public Profile createProfile(String name,String avatar,boolean child,String pin){
        if(current==null)throw new IllegalStateException("Conta não autenticada.");
        try(PreparedStatement p=Database.conn().prepareStatement("INSERT INTO profiles(user_id,name,avatar,child,pin) VALUES(?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){p.setLong(1,current.id);p.setString(2,name.trim());p.setString(3,avatar);p.setInt(4,child?1:0);p.setString(5,pin==null?"":pin);p.executeUpdate();try(ResultSet r=p.getGeneratedKeys()){r.next();return new Profile(r.getLong(1),current.id,name,avatar,child,pin==null?"":pin);}}catch(Exception e){throw new RuntimeException(e);}
    }
    private void createDefaultProfile(long userId,String name,String avatar)throws SQLException{
        try(PreparedStatement p=Database.conn().prepareStatement("INSERT INTO profiles(user_id,name,avatar,child,pin) VALUES(?,?,?,0,'')")){p.setLong(1,userId);p.setString(2,name);p.setString(3,avatar);p.executeUpdate();}
    }
    public void logout(){current=null;}
}
