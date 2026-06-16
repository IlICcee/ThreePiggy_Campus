import re

with open(r'C:\java111\ThreePiggy_Campus-main (2)\ThreePiggy_Campus-main\src\main\resources\static\index.html', 'r', encoding='utf-8') as f:
    content = f.read()

old_start = content.find('<svg viewBox="0 0 900 500" preserveAspectRatio="xMidYMid slice"')
old_end = content.find('</svg>', old_start) + 6

print(f'Found SVG at {old_start} to {old_end}')

new_svg = r'''<svg viewBox="0 0 900 500" preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg">
                <defs>
                    <linearGradient id="sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#A8D0E8"/><stop offset="50%" stop-color="#C8E0F2"/><stop offset="100%" stop-color="#E8F2F8"/></linearGradient>
                    <linearGradient id="groundGrad" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#B4D0B0"/><stop offset="100%" stop-color="#90B088"/></linearGradient>
                    <radialGradient id="sunGlow" cx="0.85" cy="0.12" r="0.3"><stop offset="0%" stop-color="#FFFDE8" stop-opacity="0.6"/><stop offset="100%" stop-color="transparent"/></radialGradient>
                </defs>
                <rect width="900" height="500" fill="url(#sky)"/>
                <rect width="900" height="500" fill="url(#sunGlow)"/>
                <circle cx="760" cy="55" r="18" fill="#FFFDE8" opacity="0.8"/><circle cx="760" cy="55" r="28" fill="#FFFDE8" opacity="0.2"/>
                <path d="M200 40 Q205 35 210 40" stroke="#8898A8" stroke-width="1" fill="none" opacity="0.5"/><path d="M215 35 Q220 30 225 35" stroke="#8898A8" stroke-width="1" fill="none" opacity="0.4"/>
                <path d="M500 30 Q504 26 508 30" stroke="#8898A8" stroke-width="0.8" fill="none" opacity="0.4"/><path d="M512 27 Q516 23 520 27" stroke="#8898A8" stroke-width="0.8" fill="none" opacity="0.3"/>
                <ellipse cx="120" cy="50" rx="55" ry="18" fill="white" opacity="0.55"/><ellipse cx="140" cy="44" rx="32" ry="12" fill="white" opacity="0.45"/><ellipse cx="100" cy="46" rx="25" ry="10" fill="white" opacity="0.4"/>
                <ellipse cx="600" cy="40" rx="48" ry="15" fill="white" opacity="0.5"/><ellipse cx="622" cy="34" rx="30" ry="10" fill="white" opacity="0.4"/>
                <ellipse cx="370" cy="65" rx="40" ry="12" fill="white" opacity="0.4"/><ellipse cx="390" cy="59" rx="24" ry="9" fill="white" opacity="0.3"/>
                <path d="M0 280 Q80 220 180 270 Q300 215 420 275 Q550 225 680 270 Q780 235 900 280 L900 350 L0 350Z" fill="#A0BBA0" opacity="0.4"/>
                <path d="M0 290 Q100 250 220 285 Q350 240 500 290 Q650 250 780 285 Q850 260 900 290 L900 350 L0 350Z" fill="#B0C8B0" opacity="0.5"/>
                <rect x="0" y="350" width="900" height="150" fill="url(#groundGrad)" opacity="0.65"/>
                <rect x="0" y="378" width="900" height="20" fill="#C0CCB8" opacity="0.55"/>
                <path d="M0 385 Q300 382 600 386 Q750 388 900 384" stroke="#B8C8B0" stroke-width="1.5" fill="none" opacity="0.4"/>
                <rect x="400" y="395" width="16" height="60" fill="#C0CCB8" opacity="0.4" rx="2"/>
                <rect x="250" y="395" width="12" height="55" fill="#C0CCB8" opacity="0.35" rx="2"/>
                <rect x="20" y="260" width="24" height="8" fill="#B0B8C0"/><rect x="20" y="240" width="24" height="8" fill="#B0B8C0"/>
                <rect x="28" y="245" width="16" height="123" fill="#C0C8D0" rx="1"/><rect x="28" y="240" width="16" height="8" fill="#B0BCC8" rx="2"/>
                <rect x="48" y="260" width="24" height="8" fill="#B0B8C0"/><rect x="48" y="240" width="24" height="8" fill="#B0B8C0"/>
                <rect x="56" y="245" width="16" height="123" fill="#C0C8D0" rx="1"/><rect x="56" y="240" width="16" height="8" fill="#B0BCC8" rx="2"/>
                <rect x="28" y="242" width="44" height="4" fill="#A0A8B8" rx="1"/>
                <text x="50" y="235" text-anchor="middle" fill="#8890A0" font-size="5" font-weight="700">校门</text>
                <rect x="90" y="195" width="130" height="175" fill="#D0DAE8" rx="2"/><rect x="90" y="190" width="130" height="10" fill="#C0CCDD" rx="2"/>
                <rect x="98" y="206" width="22" height="30" fill="#A8C0D8" rx="1"/><rect x="126" y="206" width="22" height="30" fill="#A8C0D8" rx="1"/><rect x="154" y="206" width="22" height="30" fill="#A8C0D8" rx="1"/>
                <rect x="98" y="246" width="22" height="30" fill="#A8C0D8" rx="1"/><rect x="126" y="246" width="22" height="30" fill="#A8C0D8" rx="1"/><rect x="154" y="246" width="22" height="30" fill="#A8C0D8" rx="1"/>
                <rect x="98" y="286" width="22" height="30" fill="#A8C0D8" rx="1"/><rect x="126" y="286" width="22" height="30" fill="#A8C0D8" rx="1"/><rect x="154" y="286" width="22" height="30" fill="#A8C0D8" rx="1"/>
                <rect x="90" y="335" width="130" height="38" fill="#C4D0E0" rx="2"/><rect x="120" y="340" width="60" height="28" fill="#A8BCD4" rx="2"/>
                <text x="155" y="198" text-anchor="middle" fill="#7A8FA8" font-size="6" font-weight="600">教学楼</text>
                <rect x="220" y="140" width="2" height="232" fill="#B8C0C8"/><rect x="216" y="140" width="16" height="10" fill="#E05048" rx="1"/>
                <rect x="310" y="175" width="170" height="198" fill="#D8E0EC" rx="3"/><rect x="310" y="170" width="170" height="10" fill="#C4D0E0" rx="3"/>
                <rect x="320" y="188" width="28" height="26" fill="#ACBDD0" rx="1"/><rect x="356" y="188" width="28" height="26" fill="#ACBDD0" rx="1"/><rect x="392" y="188" width="28" height="26" fill="#ACBDD0" rx="1"/><rect x="428" y="188" width="28" height="26" fill="#ACBDD0" rx="1"/>
                <rect x="320" y="224" width="28" height="26" fill="#ACBDD0" rx="1"/><rect x="356" y="224" width="28" height="26" fill="#ACBDD0" rx="1"/><rect x="392" y="224" width="28" height="26" fill="#ACBDD0" rx="1"/><rect x="428" y="224" width="28" height="26" fill="#ACBDD0" rx="1"/>
                <rect x="320" y="260" width="64" height="26" fill="#ACBDD0" rx="1"/><rect x="392" y="260" width="64" height="26" fill="#ACBDD0" rx="1"/>
                <rect x="310" y="337" width="170" height="38" fill="#C8D4E4" rx="2"/><rect x="350" y="343" width="90" height="28" fill="#A8BCD4" rx="2"/>
                <text x="395" y="178" text-anchor="middle" fill="#7A8FA8" font-size="6" font-weight="600">图书馆</text>
                <path d="M340 170 Q395 122 450 170Z" fill="#DCE0EC" opacity="0.75"/>
                <rect x="330" y="373" width="130" height="4" fill="#C8D4E0" rx="1"/><rect x="320" y="377" width="150" height="4" fill="#BCC8D8" rx="1"/>
                <rect x="520" y="210" width="120" height="162" fill="#D8DCE8" rx="2"/><rect x="520" y="205" width="120" height="10" fill="#C4CCDD" rx="2"/>
                <rect x="530" y="222" width="20" height="28" fill="#B0C0D4" rx="1"/><rect x="558" y="222" width="20" height="28" fill="#B0C0D4" rx="1"/><rect x="586" y="222" width="20" height="28" fill="#B0C0D4" rx="1"/>
                <rect x="530" y="260" width="20" height="28" fill="#B0C0D4" rx="1"/><rect x="558" y="260" width="20" height="28" fill="#B0C0D4" rx="1"/><rect x="586" y="260" width="20" height="28" fill="#B0C0D4" rx="1"/>
                <rect x="530" y="298" width="20" height="28" fill="#B0C0D4" rx="1"/><rect x="558" y="298" width="20" height="28" fill="#B0C0D4" rx="1"/><rect x="586" y="298" width="20" height="28" fill="#B0C0D4" rx="1"/>
                <rect x="520" y="337" width="120" height="38" fill="#CCD4E4" rx="2"/><rect x="550" y="343" width="60" height="26" fill="#A8B8D0" rx="2"/>
                <text x="580" y="212" text-anchor="middle" fill="#8090A8" font-size="6" font-weight="600">行政楼</text>
                <rect x="680" y="235" width="150" height="140" fill="#E4DCD4" rx="3"/><rect x="680" y="230" width="150" height="10" fill="#D4CCC4" rx="3"/>
                <rect x="690" y="248" width="32" height="26" fill="#CCBEAA" rx="1"/><rect x="730" y="248" width="32" height="26" fill="#CCBEAA" rx="1"/><rect x="770" y="248" width="32" height="26" fill="#CCBEAA" rx="1"/>
                <rect x="690" y="285" width="32" height="26" fill="#CCBEAA" rx="1"/><rect x="730" y="285" width="32" height="26" fill="#CCBEAA" rx="1"/><rect x="770" y="285" width="32" height="26" fill="#CCBEAA" rx="1"/>
                <rect x="680" y="338" width="150" height="38" fill="#D8D0C8" rx="2"/><rect x="720" y="344" width="70" height="26" fill="#C4B8A8" rx="2"/>
                <text x="755" y="237" text-anchor="middle" fill="#9A8E80" font-size="6" font-weight="600">食堂</text>
                <rect x="770" y="200" width="14" height="32" fill="#D4CCC4" rx="1"/>
                <ellipse cx="777" cy="196" rx="10" ry="5" fill="#E8E4DC" opacity="0.6"/>
                <circle cx="777" cy="188" r="4" fill="#E8E8E4" opacity="0.35"/><circle cx="780" cy="180" r="3" fill="#E8E8E4" opacity="0.2"/>
                <rect x="845" y="220" width="55" height="152" fill="#DCD8E4" rx="2"/><rect x="845" y="215" width="55" height="10" fill="#D0CCD8" rx="2"/>
                <rect x="852" y="230" width="14" height="22" fill="#C0B8D0" rx="1"/><rect x="872" y="230" width="14" height="22" fill="#C0B8D0" rx="1"/>
                <rect x="852" y="260" width="14" height="22" fill="#C0B8D0" rx="1"/><rect x="872" y="260" width="14" height="22" fill="#C0B8D0" rx="1"/>
                <rect x="852" y="290" width="14" height="22" fill="#C0B8D0" rx="1"/><rect x="872" y="290" width="14" height="22" fill="#C0B8D0" rx="1"/>
                <rect x="852" y="320" width="14" height="22" fill="#C0B8D0" rx="1"/><rect x="872" y="320" width="14" height="22" fill="#C0B8D0" rx="1"/>
                <rect x="845" y="340" width="55" height="34" fill="#D4D0DC" rx="2"/><rect x="858" y="346" width="30" height="22" fill="#C0B8CC" rx="2"/>
                <text x="872" y="222" text-anchor="middle" fill="#9088A0" font-size="5" font-weight="600">宿舍</text>
                <rect x="240" y="370" width="70" height="45" fill="#D0C898" opacity="0.5" rx="2"/>
                <rect x="242" y="372" width="66" height="41" fill="none" stroke="#C0B888" stroke-width="1" opacity="0.5"/>
                <circle cx="275" cy="392" r="5" fill="none" stroke="#C0B888" stroke-width="0.8" opacity="0.5"/>
                <line x1="275" y1="372" x2="275" y2="412" stroke="#C0B888" stroke-width="0.8" opacity="0.4"/>
                <rect x="620" y="365" width="200" height="50" fill="#C8D8C0" opacity="0.6" rx="4"/>
                <ellipse cx="720" cy="390" rx="80" ry="22" fill="none" stroke="#B8C8A0" stroke-width="3" opacity="0.5"/>
                <ellipse cx="720" cy="390" rx="68" ry="17" fill="none" stroke="#B8C8A0" stroke-width="1.5" opacity="0.4"/>
                <line x1="720" y1="370" x2="720" y2="410" stroke="#B8C8A0" stroke-width="1" opacity="0.35"/>
                <text x="720" y="420" text-anchor="middle" fill="#8A9A7A" font-size="5" font-weight="500">操场</text>
                <ellipse cx="420" cy="395" rx="25" ry="10" fill="#B8D4E8" opacity="0.5"/>
                <ellipse cx="420" cy="395" rx="18" ry="7" fill="#C8E0F0" opacity="0.4"/>
                <ellipse cx="420" cy="395" rx="8" ry="3" fill="#D8E8F4" opacity="0.3"/>
                <circle cx="260" cy="340" r="22" fill="#90B890" opacity="0.55"/><circle cx="275" cy="345" r="16" fill="#A0C8A0" opacity="0.45"/>
                <rect x="258" y="358" width="6" height="18" fill="#A89880" rx="1"/><rect x="273" y="362" width="5" height="14" fill="#A89880" rx="1"/>
                <circle cx="45" cy="345" r="16" fill="#98C098" opacity="0.5"/><rect x="42" y="358" width="5" height="14" fill="#A89880" rx="1"/>
                <circle cx="205" cy="348" r="12" fill="#A0C8A0" opacity="0.45"/><rect x="203" y="360" width="4" height="10" fill="#A89880" rx="1"/>
                <circle cx="500" cy="345" r="18" fill="#98C098" opacity="0.5"/><rect x="497" y="360" width="5" height="15" fill="#A89880" rx="1"/>
                <circle cx="620" cy="348" r="14" fill="#A8C8A8" opacity="0.45"/><rect x="617" y="362" width="5" height="12" fill="#A89880" rx="1"/>
                <circle cx="840" cy="348" r="16" fill="#98C098" opacity="0.45"/><rect x="838" y="362" width="5" height="14" fill="#A89880" rx="1"/>
                <ellipse cx="305" cy="378" rx="14" ry="7" fill="#98C090" opacity="0.45"/><ellipse cx="310" cy="380" rx="8" ry="4" fill="#A8D0A0" opacity="0.35"/>
                <ellipse cx="540" cy="378" rx="12" ry="6" fill="#98C090" opacity="0.4"/>
                <circle cx="295" cy="382" r="3" fill="#707888" opacity="0.5"/><line x1="295" y1="385" x2="295" y2="392" stroke="#707888" stroke-width="1.5" opacity="0.5"/>
                <circle cx="335" cy="384" r="3" fill="#707888" opacity="0.5"/><line x1="335" y1="387" x2="335" y2="394" stroke="#707888" stroke-width="1.5" opacity="0.5"/>
                <circle cx="480" cy="383" r="3" fill="#707888" opacity="0.5"/><line x1="480" y1="386" x2="480" y2="393" stroke="#707888" stroke-width="1.5" opacity="0.5"/>
                <circle cx="560" cy="382" r="2.5" fill="#707888" opacity="0.45"/><line x1="560" y1="385" x2="560" y2="390" stroke="#707888" stroke-width="1.2" opacity="0.45"/>
                <circle cx="650" cy="385" r="3" fill="#707888" opacity="0.5"/><line x1="650" y1="388" x2="650" y2="395" stroke="#707888" stroke-width="1.5" opacity="0.5"/>
                <circle cx="750" cy="386" r="2.5" fill="#707888" opacity="0.45"/><line x1="750" y1="389" x2="750" y2="394" stroke="#707888" stroke-width="1.2" opacity="0.45"/>
                <circle cx="800" cy="384" r="3" fill="#707888" opacity="0.5"/><line x1="800" y1="387" x2="800" y2="394" stroke="#707888" stroke-width="1.5" opacity="0.5"/>
                <rect x="155" y="355" width="2" height="20" fill="#B0A890" opacity="0.5"/><circle cx="156" cy="353" r="3" fill="#E8E0C8" opacity="0.45"/>
                <rect x="440" y="358" width="2" height="18" fill="#B0A890" opacity="0.5"/><circle cx="441" cy="356" r="3" fill="#E8E0C8" opacity="0.45"/>
                <rect x="700" y="360" width="2" height="16" fill="#B0A890" opacity="0.5"/><circle cx="701" cy="358" r="3" fill="#E8E0C8" opacity="0.45"/>
                <circle cx="265" cy="392" r="5" fill="none" stroke="#889098" stroke-width="1" opacity="0.4"/><circle cx="275" cy="392" r="5" fill="none" stroke="#889098" stroke-width="1" opacity="0.4"/>
                <line x1="265" y1="392" x2="275" y2="392" stroke="#889098" stroke-width="0.8" opacity="0.4"/>
                <line x1="270" y1="387" x2="270" y2="392" stroke="#889098" stroke-width="0.8" opacity="0.4"/><line x1="268" y1="387" x2="272" y2="387" stroke="#889098" stroke-width="0.8" opacity="0.4"/>'''

content = content[:old_start] + new_svg + content[old_end:]

with open(r'C:\java111\ThreePiggy_Campus-main (2)\ThreePiggy_Campus-main\src\main\resources\static\index.html', 'w', encoding='utf-8') as f:
    f.write(content)
print('Done - SVG replaced successfully')
