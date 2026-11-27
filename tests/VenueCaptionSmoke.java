package org.oxideshift.corner;

/** Venue select caption reading: letters per word from ink columns, and only exact venue patterns answer. */
public final class VenueCaptionSmoke {
    static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
    /** Columns for words of the given letter counts: 6-column letters, 2-column letter gaps, `space`-column spaces. */
    static boolean[] caption(int space,int... words){
        java.util.List<Boolean> c=new java.util.ArrayList<>();
        for(int w=0;w<words.length;w++){
            if(w>0)for(int i=0;i<space;i++)c.add(false);
            for(int l=0;l<words[w];l++){
                if(l>0){c.add(false);c.add(false);}
                for(int i=0;i<6;i++)c.add(true);
            }
        }
        boolean[] out=new boolean[c.size()+10];for(int i=0;i<c.size();i++)out[i+5]=c.get(i);
        return out;
    }
    public static void main(String[] args){
        check(java.util.Arrays.equals(VenueCaption.words(caption(9,5,4,6,3),6),new int[]{5,4,6,3}),"word split");
        check("Windy City Boxing Gym".equals(VenueCaption.match(VenueCaption.words(caption(9,5,4,6,3),6))),"Windy City");
        check("The Warehouse".equals(VenueCaption.match(new int[]{3,9})) && "Aragon Ballroom".equals(VenueCaption.match(new int[]{6,8})),"two-word venues");
        check("Madison Square Garden".equals(VenueCaption.match(new int[]{7,6,7})) && "Staples Center".equals(VenueCaption.match(new int[]{7,7})),"registered mark");
        check("Everlast Boxing Center".equals(VenueCaption.match(new int[]{8,6,6})) && "State Palace Theatre".equals(VenueCaption.match(new int[]{5,6,7})),"three-word venues");
        check(VenueCaption.match(new int[]{3,8})==null && VenueCaption.match(new int[]{})==null && VenueCaption.match(null)==null,"no guesses");
        // A one-column scratch adds nothing; a letter that thins out for a column stays one letter.
        boolean[] scratched=caption(9,3,9);scratched[1]=true;
        boolean[] thinned=caption(9,3,9);thinned[5+2]=false;
        check("The Warehouse".equals(VenueCaption.match(VenueCaption.words(scratched,6,2,2))),"scratch ignored");
        check("The Warehouse".equals(VenueCaption.match(VenueCaption.words(thinned,6,2,2))),"thin column bridged");
        // Spaces narrower than the threshold join the words (and then match nothing).
        check(VenueCaption.match(VenueCaption.words(caption(4,3,9),6))==null,"merged words do not match");
        for(int i=0;i<VenueCaption.NAMES.length;i++)for(int j=0;j<i;j++)
            check(!java.util.Arrays.equals(VenueCaption.LETTERS[i],VenueCaption.LETTERS[j]),"distinct patterns");
        System.out.println("Venue caption: letters per word, seven venues, registered marks and no guesses passed");
    }
}
