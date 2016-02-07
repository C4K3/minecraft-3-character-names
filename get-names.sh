CHARS="a b c d e f g h i j k l m n o p q r s t u v w x y z 0 1 2 3 4 5 6 7 8 9 _"
# 37 days in the past, to see if it was available then
PAST_TIME=$(expr $(date +%s) - 60 \* 60 \* 24 \* 37)

for A in $CHARS
do
	for B in $CHARS
	do
		for C in $CHARS
		do
			echo "Fetching $A$B$C"

			NOW=$(curl -I https://api.mojang.com/users/profiles/minecraft/$A$B$C)
			PAST=$(curl -I https://api.mojang.com/users/profiles/minecraft/$A$B$C?at=$PAST_TIME) 

			while [[ $NOW == *"429 Unknown"* ]] || [[ $PAST == *"429 Unknown"* ]]; do
				echo "Got a 429 Too Many Requests response, sleeping for 15 seconds"
				sleep 15
				NOW=$(curl -I https://api.mojang.com/users/profiles/minecraft/$A$B$C)
				PAST=$(curl -I https://api.mojang.com/users/profiles/minecraft/$A$B$C?at=$PAST_TIME) 
			done

			if [[ $NOW == *"204 No Content"* ]] && [[ $PAST == *"204 No Content"* ]]; then
				echo $A$B$C >> available-now.txt
				echo "Available now"
			elif [[ $NOW == *"204 No Content"* ]]; then
				echo $A$B$C >> soon-available.txt
				echo "Availabe in future"
			fi
		done
	done
done

