/*
 * This file is part of LocalTube.
 *
 * LocalTube is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LocalTube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LocalTube. If not, see <https://www.gnu.org/licenses/>.
 */


package com.lexus2026.localtube;

import java.util.*;

public class RecommendationEngine {

    private static final float W_CLUSTER_AFFINITY = 4.0f;
    private static final float W_COMPLETION       = 2.0f;
    private static final float W_NOVELTY          = 1.5f;
    private static final float W_RECENCY_PENALTY  = 0.5f;
    private static final float W_LONG_FORM        = 0.3f;
    private static final float W_RANDOM           = 1.2f;

    private static final float CLUSTER_MIN_RATIO  = 0.03f;
    private static final int   CLUSTER_MIN_COUNT  = 3;

    private static final Set<String> STOPWORDS = new HashSet<String>();
    static {
        String[] sw = {
            "de","del","la","el","los","las","un","una","y","a","en","con","por","para",
            "que","es","se","su","al","lo","le","me","mi","te","tu","si","no","ya",
            "the","a","an","of","in","on","at","to","is","it","he","she","we","you",
            "and","or","but","for","with","this","that","are","was","his","her",
            "ft","official","video","part","ep","cap","capitulo","episode","temporada",
            "season","s01","s02","s03","hd","mp4","mkv","720p","1080p","4k",
            "subtitulado","sub","español","english","full","nuevo","nueva","new",
            "1","2","3","4","5","6","7","8","9","10"
        };
        for (String w : sw) STOPWORDS.add(w);
    }

    private final Random rng = new Random();

    public Map<String, Integer> buildClusterIndex(List<VideoItem> allVideos) {
        Map<String, Integer> freq = new HashMap<String, Integer>();
        for (VideoItem v : allVideos) {
            Set<String> seen = tokenize(v);
            for (String token : seen) {
                Integer cnt = freq.get(token);
                freq.put(token, cnt == null ? 1 : cnt + 1);
            }
        }
        int total = allVideos.size();
        int minCount = Math.max(CLUSTER_MIN_COUNT, (int)(total * CLUSTER_MIN_RATIO));
        Map<String, Integer> clusters = new HashMap<String, Integer>();
        for (Map.Entry<String, Integer> e : freq.entrySet()) {
            if (e.getValue() >= minCount) clusters.put(e.getKey(), e.getValue());
        }
        return clusters;
    }

    public void assignClusters(List<VideoItem> allVideos, Map<String, Integer> clusterIndex) {
        for (VideoItem v : allVideos) {
            if (v.genre != null && !v.genre.isEmpty()) continue;
            Set<String> tokens = tokenize(v);
            String bestCluster = "general";
            int bestFreq = 0;
            for (String token : tokens) {
                Integer freq = clusterIndex.get(token);
                if (freq != null && freq > bestFreq) {
                    bestFreq = freq;
                    bestCluster = token;
                }
            }
            v.genre = bestCluster;
        }
    }

    public List<VideoItem> recommend(List<VideoItem> allVideos, int maxResults) {
        if (allVideos == null || allVideos.isEmpty()) return new ArrayList<VideoItem>();

        Map<String, Integer> clusterIndex = buildClusterIndex(allVideos);
        assignClusters(allVideos, clusterIndex);

        boolean hasHistory = hasWatchHistory(allVideos);
        Map<String, Float> clusterAffinity = buildClusterAffinity(allVideos);
        long now = System.currentTimeMillis();

        for (VideoItem v : allVideos) {
            float score = 0f;

            if (hasHistory) {
                Float cs = clusterAffinity.get(v.genre);
                if (cs != null) score += cs * W_CLUSTER_AFFINITY;
                score += v.completionRate * W_COMPLETION;
                if (v.playCount == 0) score += W_NOVELTY;
                if (v.lastWatched > 0) {
                    long ageMs = now - v.lastWatched;
                    if (ageMs < 2 * 3600000L) {
                        score -= W_RECENCY_PENALTY * (1f - ageMs / (2f * 3600000L));
                    }
                }
                if (v.duration > 0) {
                    float mins = v.duration / 60000f;
                    score += Math.min(mins / 60f, 1f) * W_LONG_FORM;
                }
                score += rng.nextFloat() * 0.4f;
            } else {
                score = rng.nextFloat() * W_RANDOM;
            }

            v.score = score;
        }

        List<VideoItem> sorted = new ArrayList<VideoItem>(allVideos);
        Collections.sort(sorted, new Comparator<VideoItem>() {
				@Override public int compare(VideoItem a, VideoItem b) {
					return Float.compare(b.score, a.score);
				}
			});

        return diversify(sorted, maxResults);
    }

    public List<VideoItem> getContinueWatching(List<VideoItem> allVideos) {
        List<VideoItem> out = new ArrayList<VideoItem>();
        for (VideoItem v : allVideos) {
            if (v.lastPosition > 0 && v.duration > 0) {
                float pct = (float) v.lastPosition / v.duration;
                if (pct > 0.05f && pct < 0.90f) out.add(v);
            }
        }
        Collections.sort(out, new Comparator<VideoItem>() {
				@Override public int compare(VideoItem a, VideoItem b) {
					return Long.compare(b.lastWatched, a.lastWatched);
				}
			});
        return out.size() > 8 ? out.subList(0, 8) : out;
    }

    private Set<String> tokenize(VideoItem v) {
        String text = ((v.title    != null ? v.title    : "") + " " +
			(v.fileName != null ? v.fileName : "") + " " +
			(v.category != null ? v.category : "")).toLowerCase();
        text = text.replaceAll("[_\\-\\.\\[\\]\\(\\)]+", " ");
        text = text.replaceAll("[^a-záéíóúüña-z0-9 ]", " ");
        String[] parts = text.trim().split("\\s+");
        Set<String> tokens = new HashSet<String>();
        for (String p : parts) {
            if (p.length() >= 3 && !STOPWORDS.contains(p)) {
                tokens.add(p);
            }
        }
        return tokens;
    }

    private boolean hasWatchHistory(List<VideoItem> videos) {
        for (VideoItem v : videos) {
            if (v.playCount > 0 || v.totalWatchTime > 0) return true;
        }
        return false;
    }

    private Map<String, Float> buildClusterAffinity(List<VideoItem> videos) {
        Map<String, Float> affinity = new HashMap<String, Float>();
        float totalWatchTime = 0;
        for (VideoItem v : videos) totalWatchTime += v.totalWatchTime;
        if (totalWatchTime == 0) return affinity;

        Map<String, Float> clusterWatch = new HashMap<String, Float>();
        for (VideoItem v : videos) {
            if (v.genre == null) continue;
            Float cur = clusterWatch.get(v.genre);
            clusterWatch.put(v.genre, (cur == null ? 0f : cur) + v.totalWatchTime);
        }
        for (Map.Entry<String, Float> e : clusterWatch.entrySet()) {
            affinity.put(e.getKey(), e.getValue() / totalWatchTime);
        }
        return affinity;
    }

    private List<VideoItem> diversify(List<VideoItem> sorted, int max) {
        List<VideoItem> result = new ArrayList<VideoItem>();
        Map<String, Integer> clusterCount = new HashMap<String, Integer>();
        int maxPerCluster = Math.max(3, max / 5);

        for (VideoItem v : sorted) {
            if (result.size() >= max) break;
            String g = v.genre == null ? "general" : v.genre;
            Integer cnt = clusterCount.get(g);
            if (cnt == null) cnt = 0;
            if (cnt < maxPerCluster) {
                result.add(v);
                clusterCount.put(g, cnt + 1);
            }
        }
        if (result.size() < max) {
            for (VideoItem v : sorted) {
                if (result.size() >= max) break;
                if (!result.contains(v)) result.add(v);
            }
        }
        return result;
    }
}
