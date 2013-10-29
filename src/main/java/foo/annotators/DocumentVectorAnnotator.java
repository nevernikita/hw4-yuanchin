package foo.annotators;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSList;
import org.apache.uima.jcas.cas.IntegerArray;
import org.apache.uima.jcas.cas.StringArray;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;

import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations.CorefChainAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
//import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.CoreMap;
import foo.VectorSpaceRetrieval;
import foo.typesystems.Document;
import foo.typesystems.Token;
import foo.utils.Utils;

public class DocumentVectorAnnotator extends JCasAnnotator_ImplBase {
  public HashMap<String, Integer> stopWordMap;
  
  
  @Override
  public void initialize(UimaContext aContext) throws ResourceInitializationException{
    try {
      URL stopwordUrl = DocumentVectorAnnotator.class.getResource("/stopwords.txt");
      if (stopwordUrl == null) {
         throw new IllegalArgumentException("Error opening src/main/resources/stopwords.txt");
      }
      stopWordMap = new HashMap<String, Integer>();
      //File file = new File("src/main/resources/stopwords.txt");    
      //BufferedReader bf_reader = new BufferedReader(new FileReader(file));
      BufferedReader bf_reader = new BufferedReader(new InputStreamReader(stopwordUrl.openStream()));
      String result_s;
      while ((result_s = bf_reader.readLine()) != null)
      {
        if(!result_s.startsWith("#")){
          stopWordMap.put(result_s, 1);
        }
      }
      bf_reader.close();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

	@Override
	public void process(JCas jcas) throws AnalysisEngineProcessException {

		FSIterator<Annotation> iter = jcas.getAnnotationIndex().iterator();
		if (iter.isValid()) {
			iter.moveToNext();
			Document doc = (Document) iter.get();
			createTermFreqVector(jcas, doc);
		}

	}
	/**
	 * 
	 * @param jcas
	 * @param doc
	 */

	private void createTermFreqVector(JCas jcas, Document doc) {

		String docText = doc.getText();
		
		
		//construct a vector of tokens and update the tokenList in CAS
		//Create a new NLP processing class including "annotators", "tokenize, ssplit, pos, lemma" methods using Stanford NLP
	  Properties props = new Properties();
	  props.put("annotators", "tokenize, ssplit, pos, lemma");
	  
	  StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
	  edu.stanford.nlp.pipeline.Annotation document = new edu.stanford.nlp.pipeline.Annotation(docText);
	  
	  //Process the text inside the pipeline, making use of the Stanford CoreNLP toolkit
	  pipeline.annotate(document);
	  
	  //Get the  processed result of all the sentences
	  List<CoreMap> sentences = document.get(SentencesAnnotation.class);
	  HashMap<String, Integer> stemMap = new HashMap<String, Integer>();
    for(CoreMap sentence: sentences) {
      for (CoreLabel token: sentence.get(TokensAnnotation.class)) {
        //Get the stems of all the tokens in this sentence
        String tt = token.get(CoreAnnotations.LemmaAnnotation.class);
        
        //eliminate stems in the stop word list
        if(!stopWordMap.containsKey(tt)){
          if(stemMap.containsKey(tt)){
            int freq = stemMap.get(tt) + 1;
            stemMap.put(tt, freq);
          }
          else{
            stemMap.put(tt, 1);
          }
        }
        
      }
    }    
    
    //populate the token list of Document Annotation
    ArrayList<Token> stemArrayList = new ArrayList<Token>();
    Iterator iter = stemMap.entrySet().iterator(); 
    while (iter.hasNext()) { 
        Map.Entry entry = (Map.Entry) iter.next(); 
        String stem = (String)entry.getKey(); 
        int freq = (Integer)entry.getValue();
        Token t = new Token(jcas);
        t.setText(stem);
        t.setFrequency(freq);
        t.addToIndexes(jcas);
        stemArrayList.add(t);
    } 
    
    FSList stemList = new FSList(jcas);    
    stemList = Utils.fromCollectionToFSList(jcas, stemArrayList);
    
    doc.setTokenList(stemList);


	}

}
