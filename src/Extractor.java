import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

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
            System.out.println("Loaded first file " + jsons.size());
            JSONArray refJsons = (JSONArray) parser.parse(new FileReader(args[1]));
            System.out.println("Loaded second file " + +refJsons.size());

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
            jsons = null;
            System.gc();

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
            jsons = null;
            System.gc();
            System.out.println("->" + docAlignment.size());
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

                    for (int r = 0; r < refDoc.sentences.length; r++) {
                        String refSen = refDoc.sentences[r];
                        float refRegion = ((float)r)/refDoc.sentences.length;
                        if (!sen2Id.containsKey(refSen))
                            sen2Id.put(refSen, sen2Id.size());
                        int refSenID = sen2Id.get(refSen);
                        int refLen = refSen.split(" ").length;

                        int doc_start_range =(int) Math.floor(Math.max(0, refRegion - 0.2) *  doc.sentences.length);
                        int doc_end_range =(int) Math.ceil(Math.min(1, refRegion + 0.2) *  doc.sentences.length);

                        for (int s = doc_start_range; s < doc_end_range; s++) {
                            String sen = doc.sentences[s];
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
                System.out.print("Building sentence alignments: " + (refDocId + 1) + "/" + docAlignment.size() + " --> " + alignments.size() + "\r");
            }
            System.out.print("\n");

            int alignDicSize = 0;
            for (int caption : alignments.keySet())
                alignDicSize += alignments.get(caption).size();

            System.out.println(alignments.size() + " " + alignDicSize);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
