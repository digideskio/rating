package org.vaadin.rating.service;

import com.vaadin.addon.jpacontainer.JPAContainer;
import com.vaadin.addon.jpacontainer.JPAContainerFactory;
import com.vaadin.data.util.filter.Compare;
import org.vaadin.rating.service.data.Comment;
import org.vaadin.rating.service.data.Presentation;
import org.vaadin.rating.service.data.Rating;

import javax.ejb.Singleton;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.util.TypeLiteral;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.List;

@ApplicationScoped @Singleton
public class RatingService {

    private HashMap<Long, String> idToEmailMap = new HashMap<>();
    private HashMap<String, Long> emailToIdMap = new HashMap<>();

    @PersistenceContext
    private EntityManager em;

    public void sendLoginLink(String email, String appLocation) {

        Long id;
        if (emailToIdMap.containsKey(email)) id = emailToIdMap.get(email);
        else {
            id = Long.valueOf((long) (Math.random() * Long.MAX_VALUE));
            emailToIdMap.put(email, id);
            idToEmailMap.put(id, email);
        }
        int trail = appLocation.indexOf("#!");
        if (trail > 0) appLocation = appLocation.substring(0, trail);
        String url = appLocation + "#!/" + id;
        sendEmail(email, email + " can log in at " + url);

        // TODO add map cleanup to conserve memory
    }

    private void sendEmail(String email, String messageTxt) {
        System.out.println("[EMAIL] " + messageTxt);

        // TODO send email with javax.mail
        //        Session mailSession = Session.getDefaultInstance(new Properties());
        //        MimeMessage msg = new MimeMessage(mailSession);
        //        try {
        //            msg.setFrom(new InternetAddress("admin@rating.vaadin.org"));
        //            msg.addRecipient(Message.RecipientType.TO, new InternetAddress(email));
        //            msg.setSubject("Rating login");
        //            msg.setText(messageTxt);
        //            Transport.send(msg);
        //        } catch (MessagingException e) {
        //            e.printStackTrace();
        //        }
    }

    public String login(String identity) {
        try {
            return idToEmailMap.get(Long.valueOf(identity));
        } catch (Exception any) {
            return null;
        }

    }

    public void setRating(String email, Presentation presentation, double rating) {
        Query q = em.createQuery("select r from Rating r where r.presentation = :p and r.email = :e");
        q.setParameter("p", presentation);
        q.setParameter("e", email);
        List results = q.getResultList();
        if (results.isEmpty()) {
            Rating r = new Rating();
            r.setEmail(email);
            r.setPresentation(presentation);
            r.setRating(rating);
            em.persist(r);
        } else {
            Rating r = (Rating) results.get(0);
            r.setRating(rating);
            em.merge(r);
        }
        // Fire CDI event about the update
    }

    public double getRating(Presentation presentation) {
        Query q = em.createQuery("select avg(r.rating) from Rating r where r.presentation = :p");
        q.setParameter("p", presentation);
        Double average = (Double) q.getSingleResult();
        return average == null ? 0 : average.doubleValue();
    }

    public double getRating(Presentation presentation, String email) {
        Query q = em.createQuery("select r.rating from Rating r where r.presentation = :p and r.email = :e");
        q.setParameter("p", presentation);
        q.setParameter("e", email);
        try {
            return (Double) q.getSingleResult();
        } catch (NoResultException e) {
            return 0.0;
        }
    }

    public JPAContainer<Comment> getComments(Presentation presentation) {
        JPAContainer<Comment> c = JPAContainerFactory.makeReadOnly(Comment.class, em);
        c.addContainerFilter(new Compare.Equal("presentation", presentation));
        return c;
    }

    public void addComment(String identity, Presentation presentation, String comment) {
        Comment c = new Comment();
        c.setEmail(identity);
        c.setPresentation(presentation);
        c.setComment(comment);
        em.persist(c);
    }

    public JPAContainer<Presentation> getPresentationsContainer() {
        return JPAContainerFactory.makeReadOnly(Presentation.class, em);
    }

    public Presentation addPresentation() {
        Presentation p = new Presentation();
        em.persist(p);
        // Fire CDI event about the update
        return p;
    }

    public void updatePresentation(Presentation presentation) {
        em.merge(presentation);
        // Fire CDI event about the update
    }

    public void deletePresentation(Presentation presentation) {
        Query q = em.createQuery("delete from Presentation p where p.id=" + presentation.getId());
        q.executeUpdate();
        // Fire CDI event about the update
    }
}
