package crosby.binary.osmosis;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.openstreetmap.osmosis.core.container.v0_6.BoundContainer;
import org.openstreetmap.osmosis.core.container.v0_6.NodeContainer;
import org.openstreetmap.osmosis.core.container.v0_6.RelationContainer;
import org.openstreetmap.osmosis.core.container.v0_6.WayContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.Bound;
import org.openstreetmap.osmosis.core.domain.v0_6.EntityType;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.OsmUser;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.RelationMember;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;

import com.google.protobuf.InvalidProtocolBufferException;

import crosby.binary.Osmformat;
import crosby.binary.BinaryParser;
import crosby.binary.file.BlockReaderAdapter;
import crosby.binary.file.FileBlock;
import crosby.binary.file.FileBlockPosition;

public class OsmosisBinaryParser extends BinaryParser implements BlockReaderAdapter {

    public void complete() {
        sink.complete();
        sink.release();
    }

    OsmUser getUser(Osmformat.Info info) {
        // System.out.println(info);
        if (info.hasUid() && info.hasUserSid())
            return new OsmUser(info.getUid(), getStringById(info.getUserSid()));
        else
            return OsmUser.NONE;
    }

    final int NOVERSION = -1;
    final int NOCHANGESET = -1;


    
    protected void parseDense(Osmformat.DenseNodes nodes) {
        long last_id = 0, last_lat = 0, last_lon = 0;
        
        int j = 0 ; // Index into the keysvals array.
        
        for (int i = 0; i < nodes.getIdCount(); i++) {
            Node tmp;
            List<Tag> tags = new ArrayList<Tag>(0);
            long lat = nodes.getLat(i) + last_lat;
            last_lat = lat;
            long lon = nodes.getLon(i) + last_lon;
            last_lon = lon;
            long id = nodes.getId(i) + last_id;
            last_id = id;
            double latf = parseLat(lat), lonf = parseLon(lon);
            if (nodes.getKeysValsCount() > 0) {
                while (nodes.getKeysVals(j) != 0) {
                    int keyid = nodes.getKeysVals(j++);
                    int valid = nodes.getKeysVals(j++);
                    tags.add(new Tag(getStringById(keyid),getStringById(valid)));
                }
                j++; // Skip over the '0' delimiter.
            }
            if (nodes.getInfoCount() > 0) {
                Osmformat.Info info = nodes.getInfo(i);
                tmp = new Node(id, info.getVersion(), getDate(info),
                        getUser(info), info.getChangeset(), tags, latf, lonf);
            } else {
                tmp = new Node(id, NOVERSION, NODATE, OsmUser.NONE,
                        NOCHANGESET, tags, latf, lonf);
            }
            sink.process(new NodeContainer(tmp));
        }
    }

    protected void parseWays(List<Osmformat.Way> ways) {
        for (Osmformat.Way i : ways) {
            List<Tag> tags = new ArrayList<Tag>();
            for (int j = 0; j < i.getKeysCount(); j++)
                tags.add(new Tag(getStringById(i.getKeys(j)), getStringById(i.getVals(j))));

            long last_id = 0;
            List<WayNode> nodes = new ArrayList<WayNode>();
            for (long j : i.getRefsList()) {
                nodes.add(new WayNode(j + last_id));
                last_id = j + last_id;
            }

            long id = i.getId();

            // long id, int version, Date timestamp, OsmUser user,
            // long changesetId, Collection<Tag> tags,
            // List<WayNode> wayNodes
            Way tmp;
            if (i.hasInfo()) {
                Osmformat.Info info = i.getInfo();
                tmp = new Way(id, info.getVersion(), getDate(info),
                        getUser(info), info.getChangeset(), tags, nodes);
            } else {
                tmp = new Way(id, NOVERSION, NODATE, OsmUser.NONE, NOCHANGESET,
                        tags, nodes);
            }
            sink.process(new WayContainer(tmp));
        }
    }

    protected void parseRelations(List<Osmformat.Relation> rels) {
        for (Osmformat.Relation i : rels) {
            List<Tag> tags = new ArrayList<Tag>();
            for (int j = 0; j < i.getKeysCount(); j++)
                tags.add(new Tag(getStringById(i.getKeys(j)), getStringById(i.getVals(j))));

            long id = i.getId();

            long last_mid = 0;
            List<RelationMember> nodes = new ArrayList<RelationMember>();
            for (int j = 0; j < i.getMemidsCount(); j++) {
                long mid = last_mid + i.getMemids(j);
                last_mid = mid;
                String role = getStringById(i.getRolesSid(j));
                EntityType etype = null;

                if (i.getTypes(j) == Osmformat.Relation.MemberType.NODE)
                    etype = EntityType.Node;
                else if (i.getTypes(j) == Osmformat.Relation.MemberType.WAY)
                    etype = EntityType.Way;
                else if (i.getTypes(j) == Osmformat.Relation.MemberType.RELATION)
                    etype = EntityType.Relation;
                else
                    assert false; // TODO; Illegal file?

                nodes.add(new RelationMember(mid, etype, role));
            }
            // long id, int version, TimestampContainer timestampContainer,
            // OsmUser user,
            // long changesetId, Collection<Tag> tags,
            // List<RelationMember> members
            Relation tmp;
            if (i.hasInfo()) {
                Osmformat.Info info = i.getInfo();
                tmp = new Relation(id, info.getVersion(), getDate(info),
                        getUser(info), info.getChangeset(), tags, nodes);
            } else {
                tmp = new Relation(id, NOVERSION, NODATE, OsmUser.NONE,
                        NOCHANGESET, tags, nodes);
            }
            sink.process(new RelationContainer(tmp));
        }
    }

    public void parse(Osmformat.HeaderBlock block) {
        double multiplier = .000000001;
        double rightf = block.getBbox().getRight() * multiplier;
        double leftf = block.getBbox().getLeft() * multiplier;
        double topf = block.getBbox().getTop() * multiplier;
        double bottomf = block.getBbox().getBottom() * multiplier;

        for (String s : block.getRequiredFeaturesList()) {
            if (s.equals("DenseNodes")) continue; // OK.
            throw new Error("File requires unknown feature: " + s);
        }
        
        String source = "http://www.openstreetmap.org/api/0.6";
        Bound bounds = new Bound(rightf, leftf, topf, bottomf, source);
        sink.process(new BoundContainer(bounds));
    }

    public void setSink(Sink sink_) {
        sink = sink_;
    }

    private Sink sink;
}