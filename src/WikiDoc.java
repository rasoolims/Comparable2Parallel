import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class WikiDoc {
    String[] sentences;
    HashMap<String, String> captionDict;
    public WikiDoc(ArrayList<String> imagePaths, ArrayList<String> captions, String[] sentences){
        this.sentences = sentences;
        captionDict = new HashMap<>();
        for (int i=0; i<imagePaths.size(); i++){
            captionDict.put(imagePaths.get(i), captions.get(i));
        }
    }

    public Set<String> imagePaths(){
        return captionDict.keySet();
    }

    public String caption(String path){
        return captionDict.get(path);
    }
}
