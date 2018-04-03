package edu.utdallas.hltri.umls;

import com.google.common.base.Throwables;
import edu.utdallas.hltri.conf.Config;
import edu.utdallas.hltri.logging.Logger;
import gov.nih.nlm.uts.webservice.content.*;
import gov.nih.nlm.uts.webservice.security.UtsWsSecurityController;
import gov.nih.nlm.uts.webservice.security.UtsWsSecurityControllerImplService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;


/**
 * Created by travis on 7/20/16.
 *
 * Wrapper for accessing UMLS and pals through NLM's UTS API
 * This might seriously be the most insane API I've ever seen
 * If you need to debug or update this code, everything I've done is from this url:
 * https://uts.nlm.nih.gov/home.html#apidocumentation
 *
 * May god have mercy on your soul.
 *
 */
public class UmlsService {
  private final static Logger log = Logger.get(UmlsService.class);
  private final static Config config = Config.load("medbase.umls");

  // From: https://uts.nlm.nih.gov/home.html#apidocumentation
  private final static String utsService = "http://umlsks.nlm.nih.gov";

  private final String username = config.getString("username");
  private final String password = config.getString("password");
  private final String umlsVersion;

  private String proxyGrantTicket = null;
  private LocalDateTime proxyGrantTicketIssued = null;

  private final UtsWsSecurityController utsSecurityService;
  private final UtsWsContentController  utsContentService;
  private final Psf utsContentConfig;

  public UmlsService() {
    this(config.getString("release"));
  }

  public UmlsService(String release) {
    this.umlsVersion = release;

    this.utsSecurityService = (new UtsWsSecurityControllerImplService()).getUtsWsSecurityControllerImplPort();
    this.utsContentService = (new UtsWsContentControllerImplService()).getUtsWsContentControllerImplPort();

    this.utsContentConfig = new Psf();
    this.utsContentConfig.setIncludeObsolete(false);      // We don't care about obsolete concepts
    this.utsContentConfig.setIncludeSuppressible(false);  // We (probably) don't care about suppressible concepts
    this.utsContentConfig.setPageLn(100);                 // No idea what the default is
    this.utsContentConfig.setCaseSensitive(false);        // We probably don't care about case sensitivity (or do we?)
    this.utsContentConfig.setIncludedLanguage("ENG");     // We almost certainly only care about English concepts
  }

  /**
   * From UMLS UTI documentation:
   * Proxy Grant Ticket (valid for 8 hours)
   * This kind of ticket validates your UTS username and password.
   * It is not necessary to obtain a new Proxy Grant Ticket each time you make a call to the API.
   * However, it is necessary to use the proxy grant ticket each time you make a call to the API to request a single-use ticket.
   * A Proxy Grant Ticket could be considered "the ticket that gets you a ticket."
   * @return proxy grant ticket
   */
  private String getProxyGrantTicket() {
    final LocalDateTime currentTime = LocalDateTime.now();
    if (proxyGrantTicket == null) {
      log.debug("Acquiring proxy grant ticket");
      try {
        this.proxyGrantTicket = utsSecurityService.getProxyGrantTicket(username, password);
        log.debug("Acquired proxy grant ticket {}", proxyGrantTicket);
      } catch (gov.nih.nlm.uts.webservice.security.UtsFault_Exception e) {
        throw Throwables.propagate(e);
      }
      this.proxyGrantTicketIssued = currentTime;
    } else if (currentTime.isAfter(proxyGrantTicketIssued.plusHours(8l))) {
      log.warn("Proxy grant ticket expired, requested new ticket");
      try {
        this.proxyGrantTicket = utsSecurityService.getProxyGrantTicket(username, password);
      } catch (gov.nih.nlm.uts.webservice.security.UtsFault_Exception e) {
        throw Throwables.propagate(e);
      }
      this.proxyGrantTicketIssued = currentTime;
    }

    return proxyGrantTicket;
  }

  /**
   * A new use ticket is required for every UTI request
   * @return single-use ticket
   */
  private String getUseTicket() {
    try {
      final String ticket = utsSecurityService.getProxyTicket(getProxyGrantTicket(), utsService);
      log.debug("Acquired use-ticket {}", ticket);
      return ticket;
    } catch (gov.nih.nlm.uts.webservice.security.UtsFault_Exception e) {
      throw Throwables.propagate(e);
    }
  }


  protected List<AtomDTO> getAtomDTOsForCui(String cui) {
    log.debug("Getting atoms for {}", cui);
    try {
      List<AtomDTO> list = utsContentService.getConceptAtoms(getUseTicket(), umlsVersion, cui, utsContentConfig);
      log.debug("Got {} atoms for {}", list.size(), cui);
      return list;
    } catch (gov.nih.nlm.uts.webservice.content.UtsFault_Exception e) {
      throw new RuntimeException(e);
    }
  }

  public List<String> getAtomsForCui(String cui) {
    return getAtomDTOsForCui(cui).stream().map(atom -> atom.getTermString().getName()).collect(Collectors.toList());
  }

  protected List<ConceptRelationDTO> getConceptRelationDTOsForCui(String cui) {
    log.debug("Getting related CUIs for {}", cui);
    try {
      List<ConceptRelationDTO> list = utsContentService.getConceptConceptRelations(getUseTicket(), umlsVersion, cui, utsContentConfig);
      log.debug("Got {} related CUIs for {}", list.size(), cui);
      return list;
    } catch (gov.nih.nlm.uts.webservice.content.UtsFault_Exception e) {
      throw new RuntimeException(e);
    }
  }

  protected List<String> getRelatedCuis(String cui, String relationType) {
    return getConceptRelationDTOsForCui(cui).stream()
                                     .filter(r -> relationType.equals(r.getRelationLabel()))
                                     .map(r -> r.getRelatedConcept().getUi())
                                     .collect(Collectors.toList());
  }

  public List<String> getBroaderCuis(String cui) {
    return getRelatedCuis(cui, "RN");
  }

  public List<String> getNarrowerCuis(String cui) {
    return getRelatedCuis(cui, "RB");
  }
}
