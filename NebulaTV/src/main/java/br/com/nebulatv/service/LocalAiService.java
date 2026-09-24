package br.com.nebulatv.service;

import br.com.nebulatv.model.Title;
import java.util.*;

public final class LocalAiService {
    public String recommend(List<Title> watched,List<Title> catalog){
        Map<String,Integer> score=new HashMap<>();
        for(Title t:watched)for(String g:genres(t.genres))score.merge(g.toLowerCase(),2,Integer::sum);
        List<Title> ranked=new ArrayList<>(catalog);ranked.removeIf(t->watched.stream().anyMatch(w->w.name.equalsIgnoreCase(t.name)));
        ranked.sort((a,b)->Integer.compare(score(b.genres),score(a.genres)));
        StringBuilder out=new StringBuilder("A Nebula IA priorizou seus gostos locais:\n\n");
        ranked.stream().limit(5).forEach(t->out.append("• ").append(t.name).append(" — ").append(t.genres).append("\n"));
        if(ranked.isEmpty())out.append("Assista alguns títulos para a IA aprender seu perfil.");
        return out.toString();
    }
    private int score(String gs){int s=0;for(String g:genres(gs))s+=g.hashCode()%7;return s;}
    private List<String> genres(String s){if(s==null)return List.of();return Arrays.stream(s.replace("["," ").replace("]"," ").replace("\"","").split(",|•|\\|/")).map(String::trim).filter(x->!x.isBlank()).toList();}
}
