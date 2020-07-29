import java.io.*;
import java.util.*;

import org.json.simple.*;
import org.json.simple.parser.*;

public class Extractor {
    public static WikiDoc getContent(JSONObject obj) {
        JSONArray imageArray = (JSONArray) obj.get("images");
        ArrayList<String> imagePaths = new ArrayList<>();
        ArrayList<String> captions = new ArrayList<>();
        for (Object imageObject : imageArray) {
            JSONObject imageObj = (JSONObject) imageObject;
            String caption = (String) imageObj.get("caption");
            String path = (String) imageObj.get("img_path");
            imagePaths.add(path);
            captions.add(caption);
        }
        String content = (String) obj.get("content");
        String[] sentences = content.split("</s>");
        sentences[0] = sentences[0].substring(sentences[0].indexOf(">") + 1).trim();

        return new WikiDoc(imagePaths, captions, sentences);
    }

    public static void main(String[] args) {
        JSONParser parser = new JSONParser();
        try {
            HashMap<Integer, HashSet<Integer>> docAlignment = new HashMap<>();

            JSONArray jsons = (JSONArray) parser.parse(new FileReader(args[0]));
            ArrayList<WikiDoc> wikiDocs = new ArrayList<>();
            HashMap<String, HashSet<Integer>> imagePathMap = new HashMap<>();
            for (int i = 0; i < jsons.size(); i++) {
                WikiDoc doc = getContent((JSONObject) jsons.get(i));
                for (String imagePath : doc.imagePaths()) {
                    if (!imagePathMap.containsKey(imagePath)) {
                        imagePathMap.put(imagePath, new HashSet<>());
                    }
                    imagePathMap.get(imagePath).add(i);
                }
                wikiDocs.add(doc);
            }
            JSONArray refJsons = (JSONArray) parser.parse(new FileReader(args[1]));
            ArrayList<WikiDoc> refWikiDocs = new ArrayList<>();
            HashMap<String, HashSet<Integer>> refImagePathMap = new HashMap<>();
            for (int i = 0; i < refJsons.size(); i++) {
                WikiDoc doc = getContent((JSONObject) refJsons.get(i));
                boolean hasImage = false;
                int docNum = refWikiDocs.size();
                for (String imagePath : doc.imagePaths()) {
                    if (!imagePathMap.containsKey(imagePath))
                        continue;
                    hasImage = true;
                    if (!refImagePathMap.containsKey(imagePath)) {
                        refImagePathMap.put(imagePath, new HashSet<>());
                    }
                    refImagePathMap.get(imagePath).add(docNum);
                    if (!docAlignment.containsKey(docNum))
                        docAlignment.put(docNum, new HashSet<>());
                    for (int docId : imagePathMap.get(imagePath)) {
                        docAlignment.get(docNum).add(docId);
                    }
                }
                if (hasImage)
                    refWikiDocs.add(doc);
            }
            System.out.println(jsons.size() + " " + refJsons.size() + "->" + docAlignment.size());
            HashMap<Integer, HashSet<Integer>> alignments = new HashMap<>();
            HashMap<String, Integer> sen2Id = new HashMap<>();

            for (int refDocId : docAlignment.keySet()) {
                WikiDoc refDoc = refWikiDocs.get(refDocId);
                Set<String> refImagePaths = refDoc.imagePaths();
                for (int docID : docAlignment.get(refDocId)) {
                    WikiDoc doc = wikiDocs.get(docID);
                    Set<String> imagePaths = doc.imagePaths();

                    Set<String> sharedImagePaths = new HashSet<>(refImagePaths);
                    sharedImagePaths.retainAll(imagePaths);

                    for (String path : sharedImagePaths) {
                        String refCaption = refDoc.caption(path);
                        if (!sen2Id.containsKey(refCaption))
                            sen2Id.put(refCaption, sen2Id.size());
                        int refCaptionID = sen2Id.get(refCaption);
                        if (!alignments.containsKey(refCaptionID))
                            alignments.put(refCaptionID, new HashSet<>());
                        String caption = doc.caption(path);
                        if (!sen2Id.containsKey(caption))
                            sen2Id.put(caption, sen2Id.size());
                        int captionID = sen2Id.get(caption);

                        alignments.get(refCaptionID).add(captionID);
                    }

                    for (String refSen : refDoc.sentences) {
                        if (!sen2Id.containsKey(refSen))
                            sen2Id.put(refSen, sen2Id.size());
                        int refSenID = sen2Id.get(refSen);
                        int refLen = refSen.split(" ").length;
                        for (String sen : doc.sentences) {
                            if (!sen2Id.containsKey(sen))
                                sen2Id.put(sen, sen2Id.size());
                            int senID = sen2Id.get(sen);

                            int senLen = sen.split(" ").length;
                            double proportion = ((double) refLen) / senLen;
                            if (Math.abs(refLen - senLen) <= 3 || (0.9 <= proportion && proportion <= 1.1)) {
                                if (!alignments.containsKey(refSenID))
                                    alignments.put(refSenID, new HashSet<>());
                                alignments.get(refSenID).add(senID);
                            }
                        }
                    }
                }
            }
            int alignDicSize = 0;
            for (int caption : alignments.keySet())
                alignDicSize += alignments.get(caption).size();

            System.out.println(alignments.size() + " " + alignDicSize);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
