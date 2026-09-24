package br.com.nebulatv.model;
public class Profile {
    public long id; public long userId; public String name="Perfil"; public String avatar="profile-01.png"; public boolean child; public String pin="";
    public Profile() {}
    public Profile(long id,long userId,String name,String avatar,boolean child,String pin){this.id=id;this.userId=userId;this.name=name;this.avatar=avatar;this.child=child;this.pin=pin;}
}
