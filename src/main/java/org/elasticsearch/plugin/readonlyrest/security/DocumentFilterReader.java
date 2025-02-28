package org.elasticsearch.plugin.readonlyrest.security;

import java.io.IOException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterDirectoryReader;
import org.apache.lucene.index.FilterLeafReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.apache.lucene.util.BitSetIterator;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.FixedBitSet;
import org.elasticsearch.ExceptionsHelper;

// https://stackoverflow.com/questions/40949286/apply-lucene-query-on-bits
// https://github.com/apache/lucene-solr/blob/master/lucene/misc/src/java/org/apache/lucene/index/PKIndexSplitter.java#L127-L170

public final class DocumentFilterReader extends FilterLeafReader {

    private final Bits liveDocs;
    private final int numDocs;

    public static DocumentFilterDirectoryReader wrap(DirectoryReader in, Query filterQuery) throws IOException {
        return new DocumentFilterDirectoryReader(in, filterQuery);
    }

    private DocumentFilterReader(LeafReader reader, Query query) throws IOException {
        super(reader);
        final IndexSearcher searcher = new IndexSearcher(this);
        searcher.setQueryCache(null);
        final boolean needsScores = false;
        final Weight preserveWeight = searcher.createNormalizedWeight(query, needsScores);

        final int maxDoc = this.in.maxDoc();
        final FixedBitSet bits = new FixedBitSet(maxDoc);
        final Scorer preverveScorer = preserveWeight.scorer(this.getContext());
        if (preverveScorer != null) {
            bits.or(preverveScorer.iterator());
        }

        if (in.hasDeletions()) {
            final Bits oldLiveDocs = in.getLiveDocs();
            assert oldLiveDocs != null;
            final DocIdSetIterator it = new BitSetIterator(bits, 0L);
            for (int i = it.nextDoc(); i != DocIdSetIterator.NO_MORE_DOCS; i = it.nextDoc()) {
                if (!oldLiveDocs.get(i)) {
                    bits.clear(i);
                }
            }
        }

        this.liveDocs = bits;
        this.numDocs = bits.cardinality();
    }

    @Override
    public int numDocs() {
        return numDocs;
    }

    @Override
    public Bits getLiveDocs() {
        return liveDocs;
    }

    @Override
    public Object getCoreCacheKey() {
        return this.in.getCoreCacheKey();
    }

    private static final class DocumentFilterDirectorySubReader extends FilterDirectoryReader.SubReaderWrapper {
        private final Query query;

        public DocumentFilterDirectorySubReader(Query filterQuery) {
            this.query = filterQuery;
        }

        @Override
        public LeafReader wrap(LeafReader reader) {
            try {
                return new DocumentFilterReader(reader, this.query);
            } catch (Exception e) {
                throw ExceptionsHelper.convertToElastic(e);
            }
        }
    }

    public static final class DocumentFilterDirectoryReader extends FilterDirectoryReader {
        private final Query filterQuery;
        
        DocumentFilterDirectoryReader(DirectoryReader in, Query filterQuery) throws IOException {
            super(in, new DocumentFilterDirectorySubReader(filterQuery));
            this.filterQuery = filterQuery;
        }

        @Override
        protected DirectoryReader doWrapDirectoryReader(DirectoryReader in) throws IOException {
            return new DocumentFilterDirectoryReader(in, this.filterQuery);
        }

        @Override 
        public Object getCoreCacheKey() {
            return this.in.getCoreCacheKey();
        }
    }
}