package org.mcsettlement.planner.baseline;
/** Test-only instrumentation. Does not affect tie-breaking or geometry unless a limit is exceeded. */
public final class BaselineBudget {
    public static int candidateChecks, pathExpanded, candidateLimit, pathLimit;
    public static void reset(int candidates,int path) {candidateChecks=pathExpanded=0;candidateLimit=candidates;pathLimit=path;}
    public static void candidate() {if(candidateChecks>=candidateLimit)throw new Limit("candidate");candidateChecks++;}
    public static void path() {if(pathExpanded>=pathLimit)throw new Limit("path");pathExpanded++;}
    public static class Limit extends RuntimeException { public Limit(String reason){super(reason+" budget exceeded");} }
}
