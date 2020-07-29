import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
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
            ArrayList<String> Id2Sen = new ArrayList<>();
            int alignDicSize = 0;
            BufferedWriter writer1 = new BufferedWriter(new FileWriter(args[2]));
            BufferedWriter writer2 = new BufferedWriter(new FileWriter(args[3]));

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
                        if (!sen2Id.containsKey(refCaption)) {
                            sen2Id.put(refCaption, sen2Id.size());
                            Id2Sen.add(refCaption);
                        }
                        int refCaptionID = sen2Id.get(refCaption);
                        if (!alignments.containsKey(refCaptionID))
                            alignments.put(refCaptionID, new HashSet<>());
                        String caption = doc.caption(path);
                        if (!sen2Id.containsKey(caption)) {
                            sen2Id.put(caption, sen2Id.size());
                            Id2Sen.add(caption);
                        }
                        int captionID = sen2Id.get(caption);

                        if (!alignments.get(refCaptionID).contains(captionID)) {
                            alignments.get(refCaptionID).add(captionID);
                            writer1.write(refCaption + "\n");
                            writer2.write(caption + "\n");
                            alignDicSize++;
                        }
                    }

                    for (int r = 0; r < refDoc.sentences.length; r++) {
                        String refSen = refDoc.sentences[r];
                        float refRegion = ((float) r) / refDoc.sentences.length;
                        if (!sen2Id.containsKey(refSen)) {
                            sen2Id.put(refSen, sen2Id.size());
                            Id2Sen.add(refSen);
                        }
                        int refSenID = sen2Id.get(refSen);
                        int refLen = refSen.split(" ").length;

                        int doc_start_range = (int) Math.floor(Math.max(0, refRegion - 0.1) * doc.sentences.length);
                        int doc_end_range = 1; // Title by title alignment
                        if (r > 0)
                            doc_end_range = (int) Math.ceil(Math.min(1, refRegion + 0.1) * doc.sentences.length);

                        for (int s = doc_start_range; s < doc_end_range; s++) {
                            String sen = doc.sentences[s];
                            if (!sen2Id.containsKey(sen)) {
                                sen2Id.put(sen, sen2Id.size());
                                Id2Sen.add(sen);
                            }
                            int senID = sen2Id.get(sen);

                            int senLen = sen.split(" ").length;
                            double proportion = ((double) refLen) / senLen;
                            if (Math.abs(refLen - senLen) <= 3 || (0.9 <= proportion && proportion <= 1.1)) {
                                if (!alignments.containsKey(refSenID))
                                    alignments.put(refSenID, new HashSet<>());
                                if (!alignments.get(refSenID).contains(senID)) {
                                    alignments.get(refSenID).add(senID);
                                    writer1.write(refSen + "\n");
                                    writer2.write(sen + "\n");
                                    alignDicSize++;
                                }
                            }
                        }
                    }
                }
                System.out.print("Building sentence alignments: " + (refDocId + 1) + "/" + docAlignment.size() + " --> " + alignments.size() + " ** " + alignDicSize + "\r");
            }
            System.out.print("\n");
            writer1.close();
            writer2.close();

            System.out.println(alignments.size() + " " + alignDicSize);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
