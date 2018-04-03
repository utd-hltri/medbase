package edu.utdallas.hltri.umls;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Created by travis on 7/20/16.
 */

public class UmlsServiceTests {
  final static UmlsService umls = new UmlsService("2016AA");


  @Test
  public void testUmlsServiceCreation() {
    assertNotNull("creating umls service", umls);
  }

  @Test
  public void testUmlsServiceConceptAtoms() {
    assertEquals(129, umls.getAtomsForCui("C0027051").size());
    assertEquals(59, umls.getAtomsForCui("C0032285").size());
  }

  @Test
  public void testUmlsServiceBroaderCuis() {
    {
      // Get broader concepts for 'Myocardial Infarction'
      final List<String> cuis = umls.getBroaderCuis("C0027051");
      // First concept is 'Cardiovascular system diseases '
      assertEquals("C1971641", cuis.get(0));
      assertEquals(6, cuis.size());
    }

    {
      // Get broader terms for 'Pneumonia'
      final List<String> cuis = umls.getBroaderCuis("C0032285");
      // First concept is 'Respiratory Tract Infections'
      assertEquals("C0035243", cuis.get(0));
      assertEquals(9, cuis.size());
    }
  }

  @Test
  public void testUmlsServiceNarrowerCuis() {
    {
      // Get narrower terms for 'Myocardial Infarction'
      final List<String> cuis = umls.getNarrowerCuis("C0027051");
      // First concept is 'Q wave MI'
      assertEquals("C0861151", cuis.get(0) );
      assertEquals(64, cuis.size());
    }

    {
      // Get narrower terms for 'Pneumonia'
      final List<String> cuis = umls.getNarrowerCuis("C0032285");
      // First concept is 'Pneumonia interstitial diffuse'
      assertEquals("C0740430", cuis.get(0));
      assertEquals(27, cuis.size());
    }
  }
}
